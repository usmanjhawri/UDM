# Nexonic Offline QA v0.5.2 — verified offline yt-dlp recovery

The user independently downloaded the official yt-dlp 2026.10.26 file (3,073,072 bytes) and we verified SHA-256 e51e69e89489f199530f13d32a4342da3ddb2cf34842f364603a066db05afbf3 against the GitHub release. Android's existing in-app updater throws FileNotFoundException despite Chrome accessing the release.

This temporary QA build has a distinct application package (com.nexonic.downloadmanager.offlineqa) and does not replace or uninstall Nexonic Personal or Nexonic Diagnostics.

## Recovery
Settings → Video engine → Import verified yt-dlp file → select downloaded file using Android Files. The app checks exact size and SHA-256, stages the file in its private engine directory, backs up the current executable, switches via rename, and updates the youtubedl-android shared preference version only after verified installation. Failed imports restore the previous executable.

## Known limitations
- Accepts only yt-dlp 2026.10.26; requires a new pinned digest for later releases.
- Does not fix the network updater itself; this bypasses its failed request.
- Does not guarantee YouTube downloads will pass; modern YouTube may require JS/EJS challenge solving.
- Do not try importing any other scripts/executables. The SHA-256 check rejects them.
- The main UDM/Nexonic app's downloads and preferences are not affected. The isolated QA app has its own queue/data.

## Phone test
1. Install the QA APK beside original app.
2. Settings → Video engine → Import verified yt-dlp file → choose verified yt-dlp from Downloads.
3. Confirm yt-dlp 2026.10.26 appears and no error remains.
4. Paste a public YouTube URL in the QA app and test 360p/720p video and audio playback.
5. If 403 still occurs, investigate JavaScript challenge/EJS support and extractor runtime environment; do not conclude network updater was the only cause.
