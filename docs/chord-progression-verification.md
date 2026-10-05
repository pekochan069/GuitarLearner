# Chord progression verification

The scope follows [issue #11's confirmed contract](https://github.com/pekochan069/GuitarLearner/issues/11#issuecomment-5993818748). This feature uses copied six-string shapes, one common tuning/capo, a musical timeline and native foreground playback. Per-chord patterns and section loops remain separate issues #29 and #30.

## Acceptance evidence

| Example | Evidence |
| --- | --- |
| C shape at capo 2 displays C and sounds D | Theory/presentation and connected editor checks cover the symbols and D3 fretboard tone. PCM uses the actual transposed MIDI tones. |
| Dotted whole lasts six quarter beats without a bar attack | `ProgressionSequencerTest` checks exact ticks/frames and one attack across the bar. `ProgressionPcmTest` checks sustained generated audio. |
| Same-shape ties sustain without another attack | Domain tests reject invalid ties and check the joined timeline. PCM tests compare tied audio with one chord of the same total duration. |
| Rest silences guitar while timing/clicks continue | PCM tests check guitar silence with clicks disabled. Timeline tests retain beat/click markers through the rest with clicks enabled. |
| Copied custom survives source changes/deletion | The connected collection journey copies a saved chord, deletes/changes its source, reloads the progression and checks a new host's restored content. |
| Unknown sounding shapes work; all-muted insertion fails | A connected host journey restores an unknown shape through the actual JSON codec and starts native playback. Storage rejects all-muted chord content and invalid ties. The editor requires a sounding string; rests have their own action. |
| Navigation/background survives; musical edits stop; tempo/click stay live | Native transport and presenter tests cover these transitions. Manual APK inspection checks the foreground service and retained MediaSession after Home, plus pause/resume. |
| Failed save retains edits; interruptions stop with a reason | Checked storage tests cover rollback and bad reads. Native host journeys cover save/delete retry, focus loss, simulated system output interruption, output failure and stale callbacks. |

## Reproduce

```powershell
.\gradlew.bat verifyModuleBoundaries lint :architecture-lint:test :domain:test :adapters:testDebugUnitTest :presentation:logic:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
$env:ANDROID_SERIAL = '<verification-device>'
.\gradlew.bat :app:connectedDebugAndroidTest
```

The exact CI static command also passed locally: `verifyModuleBoundaries lint :architecture-lint:test :domain:test :adapters:testDebugUnitTest :presentation:logic:testDebugUnitTest :app:assembleDebug`. The saved JUnit reports contain 169 cases: 168 passed and one existing recorded-guitar-fixture test skipped. Both APK builds passed.

The final full API 35 connected run passed: 73 passed, zero failures and one optional acoustic-recording test skipped. This includes all 12 progression playback tests, both progression UI journeys and the existing metronome, tuner, chord and presentation tests. Application lint passed in the same run. These are local Windows results with the CI emulator configuration; GitHub Actions results are separate.

The architecture guard script also rejected all three forbidden dependency fixtures and restored the original build file.

Failed attempts are retained in local task artifacts. Corrections preserve the native clicks and assertions: use a captured receiver for the protected system broadcast, position vertical content before horizontal fret scrolling, click visible dialog buttons without a scroll parent, and inject Save failure only after the preceding draft write commits. An existing chord tab test needed a redundant horizontal scroll removed. The existing metronome Save test now waits for the Save node's native window to regain focus and finish layout after cancellation; moving focus away from the name field was an unnecessary and unreliable test precondition. Cancel, pending confirmation, failed-save text, retained name/preset and exact command assertions remain.

## Installed APK inspection

The final production APK was installed on the dedicated emulator. A C shape was copied into a named progression, saved, looped, and edited to capo 2. Navigation and Home kept its foreground service and MediaSession in Playing; Pause changed the native session to Paused and Resume restored playback. Tempo and click changes stayed live, while the capo edit stopped the service. A force-stop and fresh launch retained the edited name, 100 BPM, capo 2 and D sounding symbol.

English light portrait and Korean dark landscape at font scale 2 were inspected. The latter uses vertical scrolling for controls and horizontal scrolling for higher frets. Device font size, automatic rotation, app locale and theme were restored afterward.

- [English playback controls](screenshots/chord-progressions/playing-en-light.png)
- [C shape with capo 2, sounding D](screenshots/chord-progressions/capo-en-light.png)
- [Korean dark landscape controls at font scale 2](screenshots/chord-progressions/controls-ko-dark-large-landscape.png)

## Audio and device limits

The user listened to the generated sample and confirmed that it sounds like guitar accompaniment. The engine synthesizes plucked harmonic voices without an added audio dependency or sample bank.

The connected rerun uses a dedicated API 35 Google APIs x86_64 emulator with the CI configuration: 360 x 800, density 160 and animations disabled. Installed-APK inspection and an earlier connected run used a separate Pixel 10a API 37.2 emulator. A shared emulator was replaced by another task's APK during early inspection, so subsequent runs use separate AVDs and explicit device serials.

The earlier API 37.2 suite had 69 passes, four failures and one optional acoustic-test skip. All 12 progression playback tests and both progression UI journeys passed. The failures were the existing metronome Save test, `preparationOnHomeHasStopAndOpenWithoutClaimingAnAudibleConfiguration` during ActivityScenario teardown, and two existing metronome playback tests (`playbackAdvancesInBackgroundAndSurvivesActivityRecreation` and `staleStopLeavesPlaybackRunningAndTheUiStopRemovesTheStartedService`) with five-second timeouts. The Save test was corrected afterward. The teardown and playback timeout causes remain unconfirmed; the complete API 37.2 suite has not been rerun with the final test correction.

No physical Android device is attached. Speaker/Bluetooth timing, physical disconnection and acoustic sustain have no measured claim here. The system-interruption test invokes a captured registered receiver because Android rejects application and shell attempts to emit the protected broadcast. Optional acoustic recording remains skipped.

Pause retains the native queued samples and oscillator state. This follows [AudioTrack's pause/flush contract](https://developer.android.com/reference/android/media/AudioTrack#pause()). Foreground preparation precedes the focus request, as required by the [audio-focus rules](https://developer.android.com/media/optimize/audio-focus).
