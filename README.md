# Jarvis (Android)

Malayalam voice/text assistant with Normal and Hybrid (red) modes.

## APK build (GitHub)
1. Create a new GitHub repo and upload everything from this folder (keep `.github/workflows/build.yml`).
2. Open the **Actions** tab -> "Build Jarvis APK" -> wait for green tick.
3. Open the run -> **Artifacts** -> download `Jarvis-APK` -> install `app-debug.apk`.

## First run
- Settings -> paste Gemini API key (aistudio.google.com) -> SAVE.
- Settings -> Permissions -> ALLOW ALL, then enable "Modify system settings" and the Jarvis Accessibility service.
- Optional: YouTube Data API v3 key for song search.
- Termux (Hybrid): install Termux from F-Droid, add `allow-external-apps=true` to `~/.termux/termux.properties`, and grant Jarvis the Termux "Run commands" permission.
- WhatsApp numbers with 10 digits get country code 91 by default (edit `waNumber` in Actions.kt).
