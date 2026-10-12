package com.creator.internetpro;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.inputmethod.EditorInfo;
import android.webkit.DownloadListener;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.webkit.URLUtil;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final String[] BLOCKED_DOMAINS = {
        "roblox.com", "rbxcdn.com", "robloxusercontent.com", "robloxapis.com", "rblx.com", "rbxlcdn.com", "rbx.com", "roblox.cn",
        "minecraft.net", "minecraftservices.com", "minecraft-services.net", "mojang.com", "mojang.net", "minecraft.wiki",
        "minecraftforum.net", "minecraftservers.org", "minecraftservers.net", "minecraft-mp.com", "planetminecraft.com",
        "tiktok.com", "tiktokv.com", "tiktokcdn.com", "tiktokcdn-us.com", "tiktokapis.com", "tiktokstatic.com",
        "ttwstatic.com", "byteoversea.com", "ibytedtos.com", "bytefcdn.com", "bytefcdn-oversea.com", "bytefcdn-ttpeu.com",
        "tiktokshop.com", "tiktokglobalshop.com", "musical.ly",
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adnxs.com", "adform.net", "taboola.com",
        "outbrain.com", "scorecardresearch.com", "serving-sys.com", "adroll.com", "criteo.com", "amazon-adsystem.com",
        "bet365.com", "betfair.com", "888casino.com", "williamhill.com", "draftkings.com", "fanduel.com", "pokerstars.com",
        "stake.com", "roobet.com", "betano.com", "unibet.com", "bwin.com", "1xbet.com", "superbet.ro", "maxbet.ro",
        "netbet.ro", "cointraffic.io", "bitmedia.io", "coinzilla.io", "a-ads.com", "coinad.com"
    };

    private static final String[] DANGEROUS_EXTENSIONS = {
        ".apk", ".apks", ".xapk", ".dex", ".jar", ".exe", ".msi", ".bat", ".cmd", ".sh", ".ps1", ".scr", ".com", ".vbs", ".hta"
    };

    private static final String[] DANGEROUS_MIME_TYPES = {
        "application/vnd.android.package-archive", "application/x-msdownload", "application/x-executable",
        "application/x-sh", "application/java-archive", "application/x-msi"
    };

    private EditText addressField;
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setPadding(12, 10, 12, 10);

        addressField = new EditText(this);
        addressField.setSingleLine(true);
        addressField.setHint("Caută sau introdu o adresă web");
        addressField.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        addressField.setImeOptions(EditorInfo.IME_ACTION_GO);
        toolbar.addView(addressField, new LinearLayout.LayoutParams(0, 48, 1));

        Button goButton = new Button(this);
        goButton.setText("Deschide");
        toolbar.addView(goButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                48));

        webView = new WebView(this);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (isBlocked(request.getUrl())) {
                    Toast.makeText(MainActivity.this, "Internet Pro a blocat acest site.", Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (isBlocked(request.getUrl())) {
                    return new WebResourceResponse(
                            "text/plain",
                            "UTF-8",
                            403,
                            "Blocked by Internet Pro",
                            null,
                            new ByteArrayInputStream(new byte[0]));
                }
                return super.shouldInterceptRequest(view, request);
            }
        });
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) ->
                handleDownload(url, userAgent, contentDisposition, mimeType));
        root.addView(toolbar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(webView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1));
        setContentView(root);

        goButton.setOnClickListener(view -> navigate());
        addressField.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                navigate();
                return true;
            }
            return false;
        });
        addressField.setText("https://www.google.com");
        navigate();
    }

    private void navigate() {
        String value = addressField.getText().toString().trim();
        if (value.isEmpty()) {
            return;
        }

        String address = value.contains("://") ? value : "https://" + value;
        try {
            URI uri = URI.create(address);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("https") && !scheme.equals("http")) {
                throw new IllegalArgumentException("Only HTTP and HTTPS are supported");
            }
            addressField.setText(uri.toString());
            webView.loadUrl(uri.toString());
        } catch (IllegalArgumentException exception) {
            Toast.makeText(this, "Introdu o adresă HTTP sau HTTPS validă.", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isBlocked(Uri uri) {
        String host = uri.getHost();
        if (host == null) {
            return false;
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        for (String domain : BLOCKED_DOMAINS) {
            if (normalizedHost.equals(domain) || normalizedHost.endsWith("." + domain)) {
                return true;
            }
        }
        return false;
    }

    private void handleDownload(String url, String userAgent, String contentDisposition, String mimeType) {
        Uri downloadUri = Uri.parse(url);
        if (isBlocked(downloadUri) || !("http".equals(downloadUri.getScheme()) || "https".equals(downloadUri.getScheme()))) {
            Toast.makeText(this, "Internet Pro a blocat această descărcare.", Toast.LENGTH_LONG).show();
            return;
        }

        String filename = URLUtil.guessFileName(url, contentDisposition, mimeType)
            .replaceAll("[\\\\/:*?\"<>|]", "_")
            .replace("..", "_");
        String normalizedName = filename.toLowerCase(Locale.ROOT);
        String normalizedMime = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        for (String extension : DANGEROUS_EXTENSIONS) {
            if (normalizedName.endsWith(extension)) {
                Toast.makeText(this, "Internet Pro a blocat un tip de fișier executabil. Verifică sursa și folosește o metodă sigură.", Toast.LENGTH_LONG).show();
                return;
            }
        }
        for (String dangerousMime : DANGEROUS_MIME_TYPES) {
            if (normalizedMime.contains(dangerousMime)) {
                Toast.makeText(this, "Internet Pro a blocat un tip de fișier executabil.", Toast.LENGTH_LONG).show();
                return;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Confirmă descărcarea")
                .setMessage("Fișierul nu a fost scanat antivirus. Descarcă doar dacă ai încredere în site.\n\n" + filename)
                .setNegativeButton("Anulează", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Descarcă", (dialog, which) -> startDownload(url, userAgent, filename, mimeType))
                .show();
    }

    private void startDownload(String url, String userAgent, String filename, String mimeType) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle(filename);
            request.setDescription("Descărcare pornită din Internet Pro");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
            if (userAgent != null && !userAgent.isEmpty()) {
                request.addRequestHeader("User-Agent", userAgent);
            }
            if (mimeType != null && !mimeType.isEmpty()) {
                request.setMimeType(mimeType);
            }
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            manager.enqueue(request);
            Toast.makeText(this, "Descărcarea a început.", Toast.LENGTH_SHORT).show();
        } catch (RuntimeException exception) {
            Toast.makeText(this, "Descărcarea nu a putut fi pornită.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}