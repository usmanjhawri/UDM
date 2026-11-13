# Nexonic Personal v0.6 — single-app delivery candidate

## Intent
Turn the user's device-verified offline yt-dlp fix into one browser-free APK that starts with a known-good engine and updates itself separately from the Android app.

## Application identity and releases
- Intended install package: `com.nexonic.downloadmanager.personal` (release).
- QA package: `com.nexonic.downloadmanager.personal.qa` (debug). **Do not use QA for permanent data.**
- Target label: Nexonic Personal. Release versionName `0.6.0`, versionCode `60000`. Future release increments must increase versionCode.
- It cannot replace old `com.nexonic.downloadmanager.debug` in-place because the package and debug signing certificate differ. Keep the old installation until data is no longer needed; no claim of automatic migration.

## Bundled proven engine
- Android CI downloads the official 2026.10.26 `yt-dlp` artifact and checks **exact size 3,073,072 bytes** and SHA-256 `e51e69e89489f199530f13d32a4342da3ddb2cf34842f364603a066db05afbf3`.
- The pinned file is bundled as an APK asset and re-verified on the phone before first-run installation, with previous executable backed up.
- This is the exact release already imported and used successfully on the user's phone, but the v0.6 bundled-first-run path is new and must still pass phone testing.
- The app retains the verified manual file importer as a recovery option, placed behind Advanced or displayed if an error occurs.

## Stable engine updater
- Uses GitHub release API via HTTPS with explicit User-Agent, redirect limits, trusted origins and timeouts; does not reuse the third-party updater's failed request path.
- Only stable `yt-dlp` asset from the precise GitHub release tag is accepted. Requires GitHub's SHA-256 digest and reported size; streams to a separate staging file, verifies hash before swap, and backs up/restores the previous engine.
- Never downgrades automatically. Defers updates during active downloads.
- Keeps daily automatic checks and one-hour cooldown for background failures. Manual retry remains available.
- GitHub release metadata digest authenticates via GitHub's HTTPS channel. **It is not an independent publisher signature**; supply-chain hardening such as verifying a publisher-signed release manifest is a separate next step.
- Nightly updates remain disabled until they can be independently verified.

## Permanent signing (REQUIRED before a genuine single-app release)
- The existing `android/setup-android-signing.sh` now targets `usmanjhawri/UDM` and creates a local PKCS#12 release keystore with a long-lived identity. It writes the keystore and password ONLY to the user's chosen protected local folder and publishes the encrypted keystore through GitHub Actions secrets.
- This is a user-owned secret; **the private keystore must NEVER be committed to GitHub or sent into this chat**. Back it up safely.
- Run the script on a trusted machine with `gh`, `openssl` and GitHub write permission. Confirm GitHub Actions release APK is signed. Every subsequent release must use the *same keystore* and a higher versionCode.
- Until the repository's release-signing secrets are configured, CI may build *unsigned release APKs*, which cannot be installed. The debug artifact uses an ephemeral key and must not be treated as an upgrade route.
- Do not uninstall the current working diagnostic/QA apps just to install this preview.

## Exclusions
No Browser, RSS Feeds or QR scanner; no NewPipe/NewTube; no changing Morphe package routing beyond configuring the new Personal package after testing.

## QA gate
1. Android CI: unit tests, release/debug builds, FFmpeg emulator checks, verified bundled asset packaging, intact R8 classes, APK signing checks.
2. On phone: first launch offline shows yt-dlp 2026.10.26 and can download a public video with audio.
3. Online check (same Wi-Fi/data as prior failure): returns "up to date" instead of FileNotFoundException; verify correct status.
4. A failed update retains a working yt-dlp executable and reports a safe error. Verify rollback with controlled QA fixtures, not a production network failure.
5. Morphe Download handoff, 360p, 720p, audio, pause/resume, completed media playback.
6. If stable signer not set up, treat the PR as draft; no claim of final in-place update support.
