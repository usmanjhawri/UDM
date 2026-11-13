# Nexonic Personal v0.3 — UX best-practices implementation

This is an incremental UX-only change based on the device-tested v0.2 source branch.

## What changed

- Reduced visual noise in the compact Morphe handoff: preview title and format selection first; filename editing, save path, and login settings behind **More options**.
- Added a repeatable Retry inspection control when yt-dlp format inspection fails. No silent download with a guessed format in compact mode.
- 56dp minimum selectable quality rows and 48dp filter chips; one row is one accessible radio selection rather than two overlapping tap targets.
- Format text says "Up to 720p · MP4" because the yt-dlp selector enforces a resolution cap, not a guaranteed exact stream.
- Estimated sizes retain the approximation marker, and are shown only when reported.
- The Download action remains visible at the bottom and has a 52dp minimum target, with reduced crowded controls.
- Downloads toolbar now keeps Search and active-queue action visible; QR, Profile Batch, Library and RSS move into an overflow menu.
- Downloads tabs use single-line All / Active / Done labels for narrower screens.
- Failed download rows may show two lines of diagnostic text, rather than truncating the reason immediately.

## Excluded from scope
No changes to VideoEngine, yt-dlp options, queue, connection logic, checkpoint behavior, app package name, or Morphe itself.

## Acceptance criteria
1. GitHub Actions Android unit tests, debug APK and FFmpeg emulator jobs succeed.
2. Morphe -> picker -> video/audio -> queue -> playback still works. Actual-device test required.
3. Retry inspection succeeds after a temporary error and does not queue duplicates.
4. TalkBack reads each quality option as a radio choice with selected state; touch controls meet 48dp baseline. Check at large font and 200% display size.
5. In both dark/light mode, text is readable and selected state is visible without relying on color alone.
6. All non-YouTube and built-in-browser download flows still work.
7. Automatic return to Morphe remains best-effort, not guaranteed.

Design references: Android Material 3 Compose accessibility, minimum touch targets, accessible color roles.
