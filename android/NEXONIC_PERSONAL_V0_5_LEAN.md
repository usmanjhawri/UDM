# Nexonic Personal v0.5 — Lean downloader, no embedded browser

## Scope
The Android-only branch removes the browser at source and APK level, not just its navigation item. Morphe direct handoff and text sharing remain supported.

### Removed
- Browser tab and WebView browser class/screen; long-press-in-browser and browser offer actions
- In-app cookies/sign-in, browser page detection, bookmarks UI, default-browser role/setting, search engine/home page controls
- Browser cookie export to yt-dlp; video links remain processed using yt-dlp only
- Hidden social-profile WebView. Public profile/playlist enumeration remains yt-dlp based, without private login
- RSS feed parser, subscription sheet, automatic background polling, stored RSS API and feed unit tests
- QR-scanner button, ZXing library, camera permission and scanner activity
- Generic web-link registration; HTTPS YouTube deep links and Android ACTION_SEND remain supported

### Settings
A much shorter first view: theme, Wi-Fi-only, file organization, concurrent downloads, audio-only format, subtitles, completion/failure notifications, yt-dlp update/version. Infrequent settings (VPN restriction, data cap, speed limit, custom folder, connections, schedule, language, list cleanup, crash reports) remain under **Advanced**.

Existing settings in the installation remain saved; removed browser/RSS settings are not used by the Android application.

## Deliberate trade-offs
- Signed-in Instagram/Facebook/private content relying on embedded browser cookies will not work without another future authentication method.
- Automatic detection of media loaded by webpage scripts is removed.
- YouTube Morphe, shared public links, manual paste, queue, yt-dlp updates, torrent download, playlists and profile batch stay available.
- YouTube link handoff must be verified on phone, particularly Morphe's ACTION_VIEW invocation after limiting manifest deep-link hosts to YouTube.

## Acceptance checklist
- Android unit tests pass, debug APK assembles, emulator ffmpeg QA passes, APK signed.
- Open v0.5 without crashes and verify only Downloads / Settings primary tabs.
- Morphe Download button invokes v0.5 native picker. Try 720p/video, audio and a previously failed 403 after yt-dlp update.
- Android Share text/plain to Nexonic works for public URLs.
- No Browser, RSS, QR or default-browser UI appears.
- Settings handles light/dark mode, increased font scale and accessible large touch targets.
- Confirm Wi-Fi-only and VPN-only restrictions and download concurrency correctly reflect prior user settings.
- Legacy user downloads, settings and checkpoint files remain untouched.
