# Nexonic Personal v0.2 — Morphe handoff

Pinned upstream: `0d433adb9be53137987d13d4203e7f92210def47` (Nexonic v0.31.1).

- Single media extraction engine: existing **yt-dlp**. Existing transfer and job code is untouched.
- ACTION_SEND and ACTION_VIEW YouTube source URLs open compact Compose format sheet directly.
- Video/Audio tabs, factual labels and available size estimates; pinned Download action and duplicate-tap guard.
- Media type and last resolution only are remembered. No signed URL, cookie or yt-dlp selector persisted.
- Background Nexonic after a queue insert for best-effort return to the initiating app.
- Non-YouTube links retain existing behavior. No external YouTube Morphe app patch is included.

## Phone acceptance

1. Check cold launch and notification permission.
2. From YouTube Morphe, invoke its external downloader, select Nexonic Personal debug if Android offers a chooser.
3. Verify format sheet opens directly; no browser navigation, no fake sizes.
4. Video 720p and audio-only each queue and produce playable files with audio as appropriate.
5. Verify manual pause/resume, no duplicate jobs on rapid tap, and that returning to Morphe is best-effort.
6. Reopen and retry same URL: quality preference should be restored only if present in fresh yt-dlp choices.

## Build status

**Unverified** until GitHub Actions completes. Android 10+, JDK 17. CI: `gradle -p android testDebugUnitTest assembleDebug` plus upstream native prerequisites. Existing APK should not be replaced: debug ID is `com.nexonic.downloadmanager.debug`.

Android distribution of the full app includes GPL-3.0 components; retain source and license obligations.
