# Internet Pro pentru Android

Acesta este proiectul sursă al aplicației Android Internet Pro. Folosește `android.webkit.WebView`, componenta WebView inclusă în Android; nu folosește Microsoft WebView2.

Protecțiile locale blochează domenii cunoscute asociate Roblox, TikTok, Minecraft, rețelelor de reclame, jocurilor de noroc și unor rețele de publicitate crypto. Lista este fixă în sursă, nu este actualizată automat și nu poate identifica fiecare reclamă sau site oglindă. Descărcările de APK/executabile/scripturi sunt blocate; alte descărcări cer confirmare, dar nu sunt scanate antivirus.

Redarea web folosește codec-urile și accelerarea oferite de Android System WebView/dispozitiv. LibVLC nu este integrat ca player separat în această versiune. Vulkan este o API grafică, nu un codec; utilizarea ei este decisă de WebView și driverul Android, nu forțată de aplicație.

## Construirea APK-ului

Deschide folderul `InternetProAndroid` în Android Studio și construiește configurația `app`. Sunt necesare JDK 17, Gradle 8.11.1 sau mai nou și Android SDK platform 36. APK-ul de debug va apărea la `app/build/outputs/apk/debug/app-debug.apk`.

Creatorul de sisteme de operare poate exporta opțiunea de includere Internet Pro în `os-config.json`. Pentru includerea binarului în pachetul exportat, APK-ul trebuie construit separat și trebuie adăugat într-un flux de instalare Android compatibil. Selectarea în plan nu instalează automat APK-ul și nu înlocuiește aplicația Samsung Internet pe telefon.