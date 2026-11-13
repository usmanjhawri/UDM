# Nexonic Personal v0.6.1 — optional bundled asset and AndroidIDE builds

## Reproduction
The original v0.6 GitHub APK bundles `assets/verified-yt-dlp-2026.10.26` and verifies it, but the repository ZIP did **not** include the binary. CI added it in a separate workflow step, so compiling the repository directly in AndroidIDE (or Android Studio) could produce an APK without the asset. On device, `BundledYtDlpBootstrap.installIfNeeded` then threw `FileNotFoundException: verified-yt-dlp-2026.10.26`. Because `VideoEngine.ensure` treated this as fatal, Settings showed an unavailable engine and even manual import was blocked.

## Fix
- `app/build.gradle.kts` now has `preparePinnedYtDlp`, a build task that downloads the fixed official 2026.10.26 file when missing and verifies the exact size (3,073,072 bytes) and SHA-256 before packaging.
- The build task runs before `preBuild` and `merge*Assets`. A verified local file can also be placed manually at `android/app/src/main/assets/verified-yt-dlp-2026.10.26`, enabling an entirely offline build.
- If a debug build cannot fetch the file, the app still builds. Startup catches missing/invalid optional asset installation, keeps the underlying yt-dlp Android library initialized and reports the recoverable problem in Settings. The user may then import the previously downloaded verified file.
- For **release builds**, a missing verified asset is a build error, not a silently broken release.
- Changes are isolated: no changes to yt-dlp extraction, download queue, Morphe handoff, or browser-free interface.

## Manual validation
1. AndroidIDE: open **android/** rather than repository root; allow Gradle sync/network access. Build a fresh Debug APK. Check the Gradle `preparePinnedYtDlp` task outcome.
2. Install QA package `com.nexonic.downloadmanager.personal.qa1` (separate from v0.6 QA); open Settings and confirm yt-dlp 2026.10.26.
3. Disable network during first app launch to prove the bundled file works offline (it was packaged at build time).
4. If the source build could not fetch the asset, confirm the app remains usable enough to import verified `yt-dlp` via Settings and no fatal engine-start failure occurs.
5. Verify public 360p/720p YouTube and audio-only downloads, Morphe handoff and pause/resume.
6. CI must build the APK and verify its asset presence and checksum.
7. Existing v0.5.2 Offline QA is the proven working reference. Never uninstall/clear app data based on this candidate.

## Signing
QA builds use ephemeral Android debug certificates; release signing is a separate user-controlled secret. This patch does not configure a production key.
