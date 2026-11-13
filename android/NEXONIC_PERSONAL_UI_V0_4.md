# Nexonic Personal v0.4 — Visual system

## Goal
Consistent, accessible Material 3 UI across the Downloads list, compact Morphe picker, and shared theme. No changes to the working yt-dlp download engine, format selectors, process handoff, queue, resume, or storage.

## UI details

- Shared neutral surfaces and a restrained cobalt accent; contrast-aware Material 3 roles for light and dark appearances.
- Theme-wide radius scale (12 / 16 / 22 / 28 dp) for consistent controls, cards and bottom sheets.
- Download cards are softly elevated with clear content hierarchy, readable two-line titles, semantic status label plus colored dot, and easy-to-hit primary action.
- A labeled extended Add Download action avoids an unlabeled + icon; search and queue actions stay near the top.
- Friendly empty state with a decorative download icon, heading, explanatory text, and existing actions.
- Morphe sheet has a clear icon/title header, compact selected media preview, tonal selection surface, and a persistent 52 dp Download action.
- Download details use flexible weighted columns instead of a fixed 96 dp label width, reducing clipping under enlarged fonts.

## QA before promotion

1. Android CI passes, all APK artifacts are signed as expected.
2. Manually inspect UI at normal, 150%, and largest system text scaling; landscape and narrow screens.
3. Verify light/dark/custom accent selections maintain readable content and selected states.
4. Turn on TalkBack and confirm download rows and the format picker are discoverable with clear labels.
5. Validate normal downloads, Morphe direct handoff, exact yt-dlp selection, manual pause/resume, and output playback on the phone.
6. Verify statuses remain textual and not conveyed solely by color.

## Non-goals
No introduction of NewPipe/NewTube, no changes to video engine options, and no unsupported promise of force-stop recovery.
