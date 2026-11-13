# Nexonic Personal v0.5.1 — update diagnostics and 403 recovery

## Root cause addressed
Previous UI swallowed the yt-dlp update exception and left an indefinite "Checking version…" label when the version was null. Startup and manual update paths were not serialized.

## Implementation
- Serialize stable background and manual stable/nightly updater access to the yt-dlp installation via a shared update lock.
- Keep automatic checks to once per day after success; apply a 1-hour cooldown to failed background checks. User-triggered retry ignores cooldown.
- Never report a failed update as successful. Keep the previous engine when update fails.
- Initialize the library before checking the installed yt-dlp version; show "Version unavailable" instead of a false endless loading state.
- Display a sanitized actionable failure reason (e.g. DNS, timeout, TLS, 403, storage); offer a safe Copy diagnostic action. URLs/tokens are omitted.
- Nightly updates are **explicit opt-in under Advanced**, not an automatic fallback.
- Label the unfinished download tab accurately (failed jobs were incorrectly counted as Active).
- Mark the customized app release v0.5.1 in About without altering the upstream app package or versionCode.
- Unit tests cover diagnostic categories and redaction.

## Not claimed
This does not prove that every HTTP 403 download failure is caused by an outdated yt-dlp engine. YouTube currently has JavaScript challenges and may require compatible EJS scripts; adding unverified remote-executed components is explicitly out of scope.

## Phone QA
- Settings shows actual yt-dlp version or a clear reason for unavailability.
- Try stable update on Wi-Fi and mobile data, capture the safe diagnostic on failure.
- If stable is up to date and extraction still fails, optionally try nightly with the risk understood.
- Refresh the previously failed YouTube URL before retrying. Compare a newly queued public YouTube download with the saved failed entry.
- Check 720p video and audio-only playback, downloads pause/resume, and Morphe integration.
- Ensure debug APK signature is compatible before suggesting install over an older debug APK; GitHub ephemeral runners can produce a new debug signing key.
