# Compact metronome verification

Verified on 2026-10-04 for [issue #7](https://github.com/pekochan069/GuitarLearner/issues/7), its [confirmed contract](https://github.com/pekochan069/GuitarLearner/issues/7#issuecomment-5976527408), the [quarter-note BPM correction](https://github.com/pekochan069/GuitarLearner/issues/7#issuecomment-5977297604), and the [fixed-grid and equal-size correction](https://github.com/pekochan069/GuitarLearner/issues/7#issuecomment-5977895105).

## Result

The main practice surface combines tempo, meter, interactive beat accents, and Start/Stop. Default 4/4 at 360 × 800 dp fits without scrolling. Static introductory content and the permanent second accent editor are removed. Presets open in a native Material 3 bottom sheet; save, load, overwrite confirmation, delete, and entered-name restoration remain available.

Meter selection uses two equally wide, labeled native Material 3 exposed dropdowns. Every beat cell has the same width and height across all rows, measured against all ordinals and localized accent labels. Accent changes and playing/pending status cannot resize or move the beat cells, Start, or Presets. The last row reserves vacant cell positions. Practice sections use 16 dp spacing.

| Beats per bar | Columns per row |
| --- | --- |
| 1–4 | Same as beats per bar |
| 6, 9, 12, 15 | 3 |
| 5, 7, 8, 10, 11, 13, 14, 16 | 4 |

Columns stay fixed at large text sizes. When the measured grid exceeds the viewport, all rows scroll horizontally together; the practice page scrolls vertically. This supersedes the earlier width-dependent wrapping. Start and Presets precede variable status and pending-change text.

BPM now always refers to quarter notes. At 90 BPM and 48 kHz, click spacing is 64,000 frames for half notes, 32,000 for quarter notes, 16,000 for eighth notes, and 8,000 for sixteenth notes. The numeric BPM, saved preset fields, and version 1 storage format are unchanged. Existing non-quarter presets therefore play at the new quarter-reference rate.

A successfully saved time-signature change during playback replaces the audio output and starts the new whole configuration at beat 1. The live session retains its service, media session, audio focus, wake lock, and interruption handlers. Output callbacks carry an epoch checked after their main-thread hop. Stop, interruptions, failures, and a later manual Start revoke an old edit's automatic restart authority. A stopped edit stays stopped. BPM-only and accent-only edits retain their respective beat/bar boundary rules.

| Option | Decision |
| --- | --- |
| Replace audio inside the live session | Selected. Preserves session ownership and avoids a second foreground-service start. |
| Recreate the whole session | Rejected. Adds focus/service churn to a meter edit. |
| Always await retired output release | Rejected. Existing pause/flush and track ownership checks suffice for the tested replacement path. |

## Automated checks

Commands ran in an isolated worktree using Android Studio's JBR. The committed dependency versions were used; unrelated dependency edits in the original checkout were preserved.

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
.\gradlew.bat verifyModuleBoundaries lint :architecture-lint:test :domain:test :adapters:testDebugUnitTest :presentation:logic:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
pwsh -File scripts/verify-boundary-guard.ps1
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

The static checks passed. JUnit XML reports 65 local tests with zero failures or skips: architecture lint 20, domain 13, adapters 22, and presenter 10. All three forbidden-dependency probes were rejected and their temporary build-file edits restored.

On the API 37.1 emulator, the complete connected rerun at the timing correction reports 30 cases: 29 passed, zero failed, and one acoustic recording case explicitly skipped. These include ten controlled-output host cases, ten presentation cases, eight native playback cases, and one foundation/restoration case. Controlled-output tests exercise the real host, storage wrapper, and service with simulated output presentation; native playback tests execute AudioTrack separately. This full run preceded the subsequent UI corrections.

The first complete connected run had one failure in the existing background/recreation test. Android logged an output underrun after a 78.56 ms worker gap exceeded the existing 40 ms queue horizon at 40 BPM quarter notes. That note spacing is unchanged by this correction. The same source and APKs passed the isolated case and then the complete rerun. The failure log was retained; no buffer, threshold, or retry-code change was made. This leaves emulator scheduling sensitivity visible rather than establishing that underruns cannot occur.

The final resource-only preset-close fix followed that suite. Both product lint tasks and both APK builds passed again. Native UI inspection confirmed `Close presets` and `프리셋 닫기`. The full suite was not repeated for that label-only change.

For the fixed-grid follow-up, module boundaries, all product lint tasks, the same 65 local tests, and both APK builds passed. Two new accent-bounds regressions first failed against the old UI, then passed with the grid. The focused native run completed 15 passing cases, one harmless 0.00003 dp row-height comparison failure, and one aborted signature-menu test blocked by its nested coroutine fixture. The row-height comparison now permits one physical pixel, matching the existing cell-size comparisons; accent rectangle comparisons remain exact. The native signature case uses ordinary JUnit UI actions with narrowly scoped coroutine setup. A three-case rerun passed both all-meter geometry cases and native live signature replacement. Together these runs provide passing coverage for all 17 selected cases, rather than one uninterrupted 17-case pass.

At the grid revision, the native Stop case passed separately with added rendered-state assertions: exact host `Stopped(User)`, service removal, cleared diagnostics, localized Stopped status, Start label, and no current-beat marker. These assertions were added after a manual capture continued showing Playing despite service removal. That observation was not reproduced by the controlled case; its screenshots and thread dump remain available. No production host change was made for it.

The user then requested removal of the ordinary bottom Stopped label. Initial and user-stopped playback now omit that label and its entire row; the Start action remains. Preparing, playing/current-beat information, interruption reasons, recoverable errors, and pending-change notices remain available. Both stopped-label resources were removed. Existing restoration, localization and native Stop assertions now verify absence of the status row plus the localized Start label. Product lint and both APK builds passed, followed by five passing selected native/UI cases covering restoration, pending state, localized errors, preferences and service Stop. The updated APK was installed on the connected Samsung phone with existing data preserved. Phone accessibility XML exposes Start and Presets without the stopped label; its screenshot showed the phone's always-on display, so visual confirmation comes from the emulator screenshot below.

During manual follow-up playback the emulator logged another underrun after a 58.172 ms worker gap exceeded the existing 40 ms queue horizon. The UI exposed the recoverable failure. Audio buffering and failure thresholds remain unchanged; the retained evidence does not establish scheduling robustness.

Meaningful regression evidence includes:

- The literal 90 BPM note-value test failed the old formula before the correction. Fractional carry is checked over 100,000 beats at 137 BPM for every note value; minimum and maximum tempos are covered.
- PCM tests verify 240 BPM sixteenth-note clicks at 3,000-frame spacing across uneven render chunks, alongside accent/mute and marker behavior.
- A separate worktree temporarily removed the audio-epoch check. The stale-callback test failed because retired output changed the replacement's Preparing state to Failed. The mutation was restored.
- Native signature edits restart at the first presented beat of the new configuration. Storage failures, stale callbacks, blocked saves across Stop/new Start, focus loss, service destruction, and replacement failure are covered by controlled host tests.
- Native quarter/eighth presentation at 90 BPM measured callback-delivery means of 666.17 ms and 357.18 ms, a rate ratio of 1.865. Native 240 BPM sixteenth delivery averaged 72.93 ms. AudioTrack timestamps were used and that cadence case reported no underruns. These software callback measurements include main-thread jitter; they do not measure acoustic accuracy.

## Native UI inspection

The fixed-grid APK was inspected through ADB screenshots and accessibility trees at 360 × 800 dp portrait and 800 × 360 dp landscape. The following 16-beat, 200% text combinations were inspected through their scroll positions:

| Language | Theme | Portrait | Landscape |
| --- | --- | --- | --- |
| English | Light | Meter fields stack; grid scrolls horizontally; lower controls reachable | Fixed grid and lower controls reached by vertical scroll |
| English | Dark | Meter fields stack; grid scrolls horizontally; lower controls reachable | Lower controls reached by vertical scroll |
| Korean | Light | Meter fields stack; four grid columns fit; lower controls reachable | Meter fields and lower controls inspected by vertical scroll |
| Korean | Dark | Meter fields stack; four grid columns fit; lower controls reachable | Fixed grid and lower controls inspected by vertical scroll |

Default English/light and Korean/dark 4/4 views show all practice controls and the preset entry without scrolling. In the English 8/8 view every beat is exactly 210 × 168 px, and both meter fields are 432 px wide. Comparing Normal and Mute snapshots found zero movement in all eight beat rectangles plus Start and Presets. Direct native touches changed a playing 4/4 configuration to 7/4 and then 7/8 while retaining the same foreground-service record.

Automated UI assertions verify equal widths/heights and the prescribed row count for all 16 supported numerators at normal and 200% text, exact bounds across accent cycles, stable transport/preset bounds when pending text appears, truthful desired/applied state, preset save/overwrite/load/delete, restoration, and actual touch bounds of at least 48 dp. The earlier compact-UI inspection exercised the full native preset journey and deleted its temporary preset. The native Slider's 44 dp input area was enlarged through the native Thumb layout; the 48 dp assertion was retained.

The existing app-wide navigation labels clip horizontally in 200% portrait text. Metronome controls remain readable and reachable. Home navigation is excluded by the confirmed contract; this is a remaining layout limitation, not an all-app large-text pass.

The following grid-review screenshots precede the ordinary stopped-label removal.

| Default English/light, 4/4 | Default Korean/dark, 4/4 |
| --- | --- |
| ![English compact practice controls](screenshots/compact-metronome/default-en-light.png) | ![Korean compact practice controls](screenshots/compact-metronome/default-ko-dark.png) |

![Eight equal beat cells in two fixed rows](screenshots/compact-metronome/eight-beat-grid.png)

| English 200% text after scroll | English landscape 200% text after scroll |
| --- | --- |
| ![Fixed grid and lower controls with large text](screenshots/compact-metronome/large-text-portrait.png) | ![Start and Presets in a short landscape window](screenshots/compact-metronome/large-text-landscape.png) |

![The same grid scrolled to its rightmost columns](screenshots/compact-metronome/large-text-portrait-right.png)

Latest Korean 8/8 view after stopped-label removal, with the original emulator configuration restored:

![Practice card ends at Presets without an ordinary stopped footer](screenshots/compact-metronome/no-stopped-footer.png)

## Review and limits

Independent comment/annotation audits and parent source reviews covered host, rendering/presenter, tempo, and the localized close label. The work used native Codex gpt-6.1-sol agents under the user's model override, so independent lanes provided no model-family diversity.

The throughput checkpoint kept scope and baseline gates first, independent read-only design/review artifacts separate, one writer for coupled production state, and the parent as the sole emulator driver. Host, compact UI, timing, and resource-label changes were verified as separate commits. No product decisions remain open.

The principles that changed concrete choices were Model the Domain (reuse selected/applied configuration and run identity), Laziness Protocol (replace audio without a new coordinator), Exhaust the Design Space (compare three architecture sketches), Separate Before Serializing Shared State (isolated checkout and single production/device owners), Sequence Verifiable Units (verify successive host/UI/timing changes), Build the Lever (reusable package-checked UI capture), Attack the Premise (measure actual Slider input bounds), Test Behavior Not Implementation (mutation and literal timing regressions), and Prove It Works (native UI and AudioTrack checks).

Physical-device timing, acoustic quarter/eighth comparison, physical route removal during replacement, pre-API-33 locale behavior, and spoken TalkBack output remain unverified. Accessibility evidence is limited to semantics, labels, actions, and input bounds. No microphone recording was made. Device density, font scale, rotation, and app theme/language preferences were restored, with playback stopped. Raw logs, XML, screenshots, mutation evidence, and the append-only decision trail remain in the local `issue-7-artifacts` directory.
