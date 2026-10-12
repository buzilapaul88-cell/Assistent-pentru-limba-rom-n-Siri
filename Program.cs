using System.Net;
using System.Net.Http.Json;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Threading.RateLimiting;
using Microsoft.AspNetCore.RateLimiting;

var builder = WebApplication.CreateBuilder(args);
builder.Services.AddHttpClient("ai", client => client.Timeout = TimeSpan.FromSeconds(45));
builder.Services.AddRateLimiter(options =>
{
    options.AddFixedWindowLimiter("chat", limiter =>
    {
        limiter.PermitLimit = 20;
        limiter.Window = TimeSpan.FromMinutes(1);
        limiter.QueueLimit = 0;
        limiter.AutoReplenishment = true;
    });
});

var app = builder.Build();
app.UseRateLimiter();

app.MapGet("/health", () => Results.Ok(new { status = "ok" }));
app.MapPost("/api/chat", async (ChatRequest request, HttpRequest httpRequest, IHttpClientFactory clients, CancellationToken cancellationToken) =>
{
    var configuredToken = Environment.GetEnvironmentVariable("ASSISTANT_CLIENT_TOKEN");
    var suppliedToken = httpRequest.Headers.Authorization.ToString();
    if (string.IsNullOrWhiteSpace(configuredToken))
    {
        return Results.Problem("Assistant API is not configured.", statusCode: StatusCodes.Status503ServiceUnavailable);
    }

    var expectedHeader = $"Bearer {configuredToken}";
    if (!TokensMatch(suppliedToken, expectedHeader))
    {
        return Results.Unauthorized();
    }

    if (string.IsNullOrWhiteSpace(request.Message) || request.Message.Length > 4000)
    {
        return Results.ValidationProblem(new Dictionary<string, string[]> { ["message"] = ["Message is required and must be at most 4000 characters."] });
    }

    var provider = request.Provider?.Trim().ToLowerInvariant();
    if (provider is not ("gemini" or "openai"))
    {
        return Results.ValidationProblem(new Dictionary<string, string[]> { ["provider"] = ["Provider must be gemini or openai."] });
    }

    var style = request.LanguageStyle?.Trim().ToLowerInvariant();
    if (style is not ("ro-ro" or "ro-md"))
    {
        return Results.ValidationProblem(new Dictionary<string, string[]> { ["languageStyle"] = ["Language style must be ro-RO or ro-MD."] });
    }

    var apiKey = Environment.GetEnvironmentVariable(provider == "gemini" ? "GEMINI_API_KEY" : "OPENAI_API_KEY");
    if (string.IsNullOrWhiteSpace(apiKey))
    {
        return Results.Problem($"The {provider} provider is not configured on the server.", statusCode: StatusCodes.Status503ServiceUnavailable);
    }

    var instruction = BuildInstruction(style);
    var client = clients.CreateClient("ai");
    try
    {
        using var upstreamRequest = provider == "gemini"
            ? CreateGeminiRequest(apiKey, instruction, request.Message)
            : CreateOpenAiRequest(apiKey, instruction, request.Message);
        using var upstreamResponse = await client.SendAsync(upstreamRequest, HttpCompletionOption.ResponseHeadersRead, cancellationToken);
        if (!upstreamResponse.IsSuccessStatusCode)
        {
            return Results.Problem("The AI provider returned an error. Check server configuration and provider access.", statusCode: StatusCodes.Status502BadGateway);
        }

        await using var stream = await upstreamResponse.Content.ReadAsStreamAsync(cancellationToken);
        using var document = await JsonDocument.ParseAsync(stream, cancellationToken: cancellationToken);
        var answer = provider == "gemini" ? ReadGeminiText(document.RootElement) : ReadOpenAiText(document.RootElement);
        if (string.IsNullOrWhiteSpace(answer))
        {
            return Results.Problem("The AI provider returned no text response.", statusCode: StatusCodes.Status502BadGateway);
        }

        return Results.Ok(new ChatResponse(answer));
    }
    catch (OperationCanceledException) when (!cancellationToken.IsCancellationRequested)
    {
        return Results.Problem("The AI provider timed out.", statusCode: StatusCodes.Status504GatewayTimeout);
    }
    catch (HttpRequestException)
    {
        return Results.Problem("The AI provider could not be reached.", statusCode: StatusCodes.Status502BadGateway);
    }
    catch (JsonException)
    {
        return Results.Problem("The AI provider returned an invalid response.", statusCode: StatusCodes.Status502BadGateway);
    }
}).RequireRateLimiting("chat");

app.Run();

static bool TokensMatch(string provided, string expected)
{
    var providedBytes = Encoding.UTF8.GetBytes(provided);
    var expectedBytes = Encoding.UTF8.GetBytes(expected);
    return providedBytes.Length == expectedBytes.Length && CryptographicOperations.FixedTimeEquals(providedBytes, expectedBytes);
}

static string BuildInstruction(string languageStyle) => languageStyle switch
{
    "ro-md" => "Răspunde în limba română, cu formulări naturale folosite în Republica Moldova. Păstrează un ton respectuos și clar; nu caricaturiza accentul și nu pretinde că este o limbă distinctă.",
    _ => "Răspunde în limba română standard (România), cu diacritice și formulări clare."
};

static HttpRequestMessage CreateGeminiRequest(string apiKey, string instruction, string message)
{
    var model = Environment.GetEnvironmentVariable("GEMINI_MODEL") ?? "gemini-3.8-flash";
    var request = new HttpRequestMessage(HttpMethod.Post, $"https://generativelanguage.googleapis.com/v1beta/models/{Uri.EscapeDataString(model)}:generateContent");
    request.Headers.Add("x-goog-api-key", apiKey);
    request.Content = JsonContent.Create(new
    {
        systemInstruction = new { parts = new[] { new { text = instruction } } },
        contents = new[] { new { role = "user", parts = new[] { new { text = message } } } }
    });
    return request;
}

static HttpRequestMessage CreateOpenAiRequest(string apiKey, string instruction, string message)
{
    var model = Environment.GetEnvironmentVariable("OPENAI_MODEL") ?? "gpt-4.1-mini";
    var request = new HttpRequestMessage(HttpMethod.Post, "https://api.openai.com/v1/responses");
    request.Headers.Authorization = new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", apiKey);
    request.Content = JsonContent.Create(new { model, instructions = instruction, input = message });
    return request;
}

static string? ReadGeminiText(JsonElement root)
{
    if (!root.TryGetProperty("candidates", out var candidates) || candidates.GetArrayLength() == 0)
    {
        return null;
    }

    var parts = candidates[0].GetProperty("content").GetProperty("parts");
    return string.Join("\n", parts.EnumerateArray()
        .Where(part => part.TryGetProperty("text", out _))
        .Select(part => part.GetProperty("text").GetString())
        .Where(text => !string.IsNullOrWhiteSpace(text)));
}

static string? ReadOpenAiText(JsonElement root)
{
    if (!root.TryGetProperty("output", out var output))
    {
        return null;
    }

    return string.Join("\n", output.EnumerateArray()
        .Where(item => item.TryGetProperty("content", out _))
        .SelectMany(item => item.GetProperty("content").EnumerateArray())
        .Where(content => content.TryGetProperty("type", out var type) && type.GetString() == "output_text")
        .Select(content => content.GetProperty("text").GetString())
        .Where(text => !string.IsNullOrWhiteSpace(text)));
}

sealed record ChatRequest(string? Provider, string? LanguageStyle, string Message);
sealed record ChatResponse(string Answer);
