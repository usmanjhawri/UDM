# Side-by-side yt-dlp diagnostic APK (temporary QA only)

- APK identifier: `com.nexonic.downloadmanager.diagnostic`
- App label: **Nexonic Diagnostics**
- Installs alongside existing `com.nexonic.downloadmanager.debug` without uninstalling it or touching its internal data.
- This is **not** a permanent second app or a release update; it is a disposable debugging build from Nexonic Personal v0.5.1.
- To diagnose: open Nexonic Diagnostics -> Settings -> Video engine -> Check for updates -> Copy safe diagnostic if it fails.
- Test a fresh public YouTube URL using Add download in Diagnostics, without changing your existing Morphe configuration.
- Diagnostic and main app have separate download histories, cookies (none), yt-dlp runtime and preferences; results may differ if main already has an updated engine.
- A green Android CI does not prove YouTube works on the user device. Any live 403 diagnosis requires actual runtime logs/errors.
- Debug builds from different CI runs use unrelated signing keys. Do not uninstall the original .debug app to install this QA package.
- Once the root cause is known, configure a durable signing key for the final single-app release.
