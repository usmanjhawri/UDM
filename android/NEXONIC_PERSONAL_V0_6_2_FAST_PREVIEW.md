# Nexonic Personal v0.6.2 — fast YouTube quality-picker preview

## User complaint / baseline
The v0.6.1 Morphe-to-quality-picker screen waits on a full yt-dlp `getInfo` call on every new URL. It can feel very slow on mobile networks, especially with several YouTube player-client requests. No measured baseline was supplied, so this release does not claim a fixed speed-up percentage.

## What changed
1. Public YouTube VOD watch/Shorts/short-link pages use yt-dlp's documented `--extractor-args youtube:player_skip=initial_data` for their **metadata inspection only**. We do NOT skip JavaScript, the player webpage, player config, or signed-stream validation and do not change actual download arguments.
2. If the quick extraction fails or returns no usable video/audio choices, a regular **full yt-dlp inspection** automatically runs.
3. The picker explicitly marks this as a quick preview; **Full list** triggers an uncached full inspection when users want maximum available resolution options. YouTube and yt-dlp may still restrict some formats.
4. In-process metadata cache holds up to 12 video IDs for 120 seconds (cache key uses only the 11-character ID, not URL tracking tokens). It caches title, quality selector, durations and estimated sizes only. **No stream URLs, cookies or headers are persisted.**
5. Duplicate simultaneous requests for the same video and inspection mode share one `FutureTask`; other sites and YouTube signed/header contexts use the unchanged full inspection.
6. On a successful yt-dlp engine update or manual verified import, cached format menus are invalidated.
7. Logs include inspection mode, duration in milliseconds, and choice count without media URLs. In compact Morphe UI, **More options** shows actual inspection seconds; repeat previews show a cache marker.
8. The source remains single-engine yt-dlp and browser/RSS/QR-free.

## Limits / risks
- yt-dlp documents that skipping `initial_data` can omit metadata or rare stream qualities. The UI labels quick previews truthfully and provides full extraction on request.
- Fast-path defaults are limited to known public 11-character VOD IDs; no live, music, clip, embeds, playlist or authenticated/non-YouTube sites are altered.
- Download-time yt-dlp extraction and file integrity are unchanged. This is metadata selection latency optimization only.
- A cold first fetch may still be dominated by Python startup, YouTube response time, EJS/JS challenges or rate limits. We have **not** measured the user's phone speed-up yet.
- In-memory cache does not survive app restart, intentionally.

## Acceptance tests
- GitHub Actions tests and debug APK compilation pass, FFmpeg Android tests pass.
- Compare cold and same-link repeat inspection seconds in v0.6.1 vs v0.6.2 using the same device and network.
- Open one video via Morphe, return, reopen: second quality menu should show cache hit.
- Try **Full list** and verify choices update; confirm no stale quality selection after a new video.
- Compare normal/quick options for 360p, 720p, 1080p, 4K where available; audio-only must remain offered.
- Test YouTube Shorts, watch, youtu.be, live (no fast mode), and standard non-YouTube video links.
- Play an actual completed video including audio. Pause/resume, 403 refresh and verified yt-dlp updater remain functional.
- Test large fonts, light/dark, no overlapping picker tabs, privacy-safe logs.
- The experimental APK has separate package `com.nexonic.downloadmanager.personal.qa2` and cannot replace the previously installed signed `.qa1`.
