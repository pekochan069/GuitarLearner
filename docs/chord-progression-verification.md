# Chord progression verification

The scope follows [issue #11's confirmed contract](https://github.com/pekochan069/GuitarLearner/issues/11#issuecomment-5993818748). Progressions contain copied six-string shapes and rests, one common tuning/capo, musical durations and native foreground playback. Per-chord patterns (#29) and section loops (#30) remain separate work.

## Acceptance evidence

| Example | Evidence |
| --- | --- |
| Add C → Am → F → G directly in the progression tool | The connected journey selects roots and qualities, checks each inserted identity and quarter-note duration, and checks the names, note lengths, top BPM/meter and fixed transport. It drags Am to the end, checks C → F → G → Am, and deletes a chord during playback without opening a sheet. |
| Drag above surrounding controls, cancel, or hold at a list edge | A native touch journey checks the dragged card crossing the list boundary over the footer, cancellation without a content change, continuous edge scrolling while held, and one accepted move to the end. The overlay is above Scaffold; keyed neighbors use Compose's native placement animation. |
| Show the playing chord on six guitar strings | Native playback checks all six visible fretboard strings. Count-in, chords and rests reserve the same pane bounds; a rest clears the previous chord's board without moving the sequence. |
| Draft persistence does not move the music | A native held-write/failure/retry journey checks the existing rows, timing control and Save action keep their bounds. Unsaved drafts use a native Badge on the existing Save icon and recovery in the More menu; the indication survives starting playback. There is no persistence paragraph or retry button in the sequence. |
| New chords and rests always append | The named journey selects the first existing chord before every later insertion and checks C → Am → F → G in order. The copied-shape journey selects the first chord before adding a rest, checks both existing chords and their tie are unchanged, and checks the rest at the end. Existing chord edits use the original indexed replacement command. |
| Large text in a short landscape viewport | The Korean dark journey checks that a complete chord row fits after scrolling, keeps the transport reachable, edits higher frets and invalid context, and saves with the native keyboard, failed-write retention and retry. Add actions scroll with the list in short viewports. |
| C shape at capo 2 displays C and sounds D | Named lookup uses the current tuning without capo to select the shape. Theory/presentation and connected editor checks cover the sounding symbol and D3 fretboard tone. PCM uses the actual transposed MIDI tones. |
| Dotted whole lasts six quarter beats without a bar attack | Sequencer tests check exact ticks/frames and one attack across the bar. PCM tests check sustained generated audio. BPM always means quarter notes per minute, independently of the meter's beat value. |
| Same-shape ties sustain; rests preserve timing | Domain tests reject invalid ties and check the joined timeline. PCM tests compare tied audio with one chord of the same total duration and check rest silence. Beat/click markers continue through rests. |
| Copied custom survives source changes/deletion | The Manual shape menu copies current or saved shapes without a separate Copy tab. The connected collection journey changes/deletes the source, reloads the progression and checks a new host's restored content. Named insertion also leaves the chord viewer's draft unchanged. |
| Unknown sounding shapes work; all-muted insertion fails | A connected host restores an unknown shape through the JSON codec and starts native playback. Storage rejects all-muted content and invalid ties. The editor requires a sounding string; rests have their own action. |
| Navigation/background survives; musical edits stop; tempo/click stay live | Native playback and presenter tests cover these transitions. Viewing a step keeps playback running; selected start closes the sheet only when accepted. |
| Failed save retains edits; replacement and interruption remain explicit | Storage tests cover rollback and bad reads. Native journeys cover save/delete retry, focus loss, simulated output interruption, output failure and stale callbacks. Presenter checks cover unsaved replacement, queued context changes and stale sheet requests. |

## Reproduce

```powershell
.\gradlew.bat verifyModuleBoundaries lint :architecture-lint:test :domain:test :adapters:testDebugUnitTest :presentation:logic:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
$env:ANDROID_SERIAL = '<verification-device>'
.\gradlew.bat :app:connectedDebugAndroidTest
```

The static command passed locally: module boundaries, product lint, architecture detector tests, unit tests and both APK builds. JUnit contains 180 cases: 179 passed, zero failed and one existing recorded-guitar-fixture test skipped. The architecture guard's three forbidden-dependency fixtures were also rejected and the build file restored during the original implementation; the guard and module dependencies have not changed in the UX repair.

The final full API 35 connected suite passed with 78 cases: 77 passed, zero failed and one optional acoustic-recording test skipped. It includes all 12 progression playback tests, six progression UI journeys and the existing metronome, tuner, chord and presentation tests. The dedicated Google APIs x86_64 emulator uses the CI configuration: 360 × 800, density 160 and animations disabled. The six UI journeys also passed with animations enabled before the append change, with captured placement-animation frames. GitHub Actions results are separate from these local results.

Failed attempts and their reports are retained in local task artifacts, including the earlier Save-retry timeout and missing popup option. The UI driver now waits for each actual control to exist and be enabled, and for Save retry readiness, while retaining the music and storage assertions. Other corrections cover coherent presenter state after asynchronous lookup, chord labels and click actions on the same accessible native row, and the UI journey sharing its host with the actual playback service. The large-text check requires the whole row to fit; partial visibility alone is insufficient.

## Installed APK inspection

The production APK was installed on the dedicated API 35 emulator. C → Am → F → G was added through the named picker without leaving the progression tool. Rows show the chord name, note length and a 48 dp delete action. BPM and meter are above the sequence; Play/Pause/Resume, Stop and Loop remain below it. Loop and Play started native playback with all six fretboard strings visible. Changing capo to 2 stopped playback and displayed C → D, Am → Bm, F → G and G → A shape/sounding pairs. The picker and playback board omit explanatory legends and redundant shape labels while retaining accessible string/pitch information.

Korean dark landscape at font scale 2 was inspected with the installed APK. In short viewports the Add controls scroll, leaving enough height to read a complete chord row while the transport stays visible. Sheet contents scroll independently of their primary action. Device font size, automatic rotation, locale and night mode were restored afterward.

- [English playback and four-chord list](screenshots/chord-progressions/playing-en-light.png)
- [Capo 2 shape and sounding names](screenshots/chord-progressions/capo-en-light.png)
- [Korean dark landscape at font scale 2](screenshots/chord-progressions/controls-ko-dark-large-landscape.png)
- [Named chord picker](screenshots/chord-progressions/named-chord-picker-ko.png)
- [Dragged chord above the player](screenshots/chord-progressions/drag-above-player.png)
- Neighbor placement [at the start](screenshots/chord-progressions/reorder-start.png) and [32 ms later](screenshots/chord-progressions/reorder-mid.png), captured with animations enabled.
- [Draft status on the Save icon](screenshots/chord-progressions/draft-save-indicator.png)
- [Final APK on the Samsung SM_S948N](screenshots/chord-progressions/phone-ko.png)

The final APK was installed with `adb install -r` on the Samsung SM_S948N and the dedicated emulator. The phone's installed `base.apk` matched SHA-256 `839A107B18613769BD78F04AA067BF12532F3FEEAC86F3348E00D9BBD6532615`. The stored progression document was unchanged across installation, MainActivity was confirmed foregrounded, and the Korean sequence was visually inspected. The phone's existing draft was preserved. Final gesture, animation and append checks are emulator evidence; physical-device performance was not measured.

## Audio and device limits

The user listened to the generated sample and confirmed that it sounds like guitar accompaniment. The engine synthesizes plucked harmonic voices without an added audio dependency or sample bank. Physical speaker/Bluetooth timing, disconnection and acoustic sustain have no measured claim here; optional acoustic recording remains skipped.

An earlier API 37.2 run, before the UX repair, had 69 passes, four failures and one optional acoustic-test skip. All 14 progression tests in that version passed. The failures were the existing metronome Save window test, `preparationOnHomeHasStopAndOpenWithoutClaimingAnAudibleConfiguration` during ActivityScenario teardown, and two existing metronome playback tests (`playbackAdvancesInBackgroundAndSurvivesActivityRecreation` and `staleStopLeavesPlaybackRunningAndTheUiStopRemovesTheStartedService`) with five-second timeouts. The Save window correction passes in the API 35 suite. The teardown and playback timeout causes remain unconfirmed; the full API 37.2 suite has not been rerun on the final revision.

The system-interruption test invokes a captured registered receiver because Android rejects application and shell attempts to emit the protected broadcast. Pause retains the native queued samples and oscillator state, following [AudioTrack's pause/flush contract](https://developer.android.com/reference/android/media/AudioTrack#pause()). Foreground preparation precedes the focus request, following the [audio-focus rules](https://developer.android.com/media/optimize/audio-focus).
