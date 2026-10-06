# Learning verification

The implementation follows [issue #36](https://github.com/pekochan069/GuitarLearner/issues/36) and its resolved decisions #33, #34 and #35. It contains six theory lessons and seven guitar technique lessons, with original bundled Korean and English explanations. Topics, Courses and Free exploration are available immediately. Courses share the same lessons and completion state as Topics.

## Acceptance evidence

| Behavior | Evidence |
| --- | --- |
| Explore musical relationships with contextual spelling | Domain fixtures and the connected journey check G major with F♯, F major with B♭, D major with D/F♯/A and degrees 1/3/5, major diatonic chords and a I–IV–V–I sequence. The fretboard exposes string, fret, note and degree descriptions. Circle controls expose each major key and its relative minor. |
| Read and practice all seven techniques | Both localized resources contain movement, sound, a whitespace-preserving TAB example and a short practice task. The layout check visits every technique, checks nonempty readable practice text, and checks compiled down/upstroke and palm-mute marker alignment. Technique lessons do not offer theory playback. |
| Complete a lesson and continue later | The connected journey checks shared Topics/Courses completion. With the emulator's active default network absent, a manual force-stop and cold launch changed PID 11459 to 11662 and restored both the Continue entry and Completed state. Stored progress was `1\|circle_of_fifths\|circle_of_fifths`. |
| Retain progress when storage fails | Adapter tests hold and fail writes, preserve optimistic completion and last-viewed state, retry the latest combined revision and recover existing stored state. Saved status follows an accepted write. |
| Play examples explicitly with Piano or Guitar | The production native output reaches Playing and finishes normally for both instruments. Navigation and background departure stop it. The native focus check verifies Progressions and Learning interrupt one another and retain a readable lesson with retry. No autoplay occurs on return. |
| Return from a relevant tool or exercise | The connected journey checks Metronome and Progressions return to the same lesson, and active Training → Setup → lesson across Activity recreation. Presenter coverage also checks reopening the same tool from an upstream launch input. |
| Use enlarged text and recovery controls | At 320 dp width, Korean and English checks cover 200% text, readable explanations/practice, 48 dp retry actions and their emitted events. Circle controls are checked for non-overlap at both 120% and 200%. |

## Checks and installed artifacts

`verifyModuleBoundaries`, product `lint`, architecture-lint tests, domain tests, adapter tests and presenter tests passed. Unit results contain 240 cases: 239 passes and one existing recorded-guitar fixture skip. The three deliberately invalid architecture fixtures were rejected.

The isolated API 35 Google APIs x86_64 emulator ran with a 360 × 800 viewport, density 160, animations disabled and no active default network. The full suite at `5e61fe4` contained 98 cases: 97 passes, zero failures and one optional acoustic-recording skip. After the final coordinate clamp at `d34f6d7`, the affected Learning layout and three production journeys all passed again, along with the static and unit checks. The full suite was not repeated for that one-line clamp.

The Samsung SM_S948N passed the production Piano/Guitar, normal completion, navigation and background journey in Korean at `550919e`, and again in English at 200% text with actual landscape rotation at `5e61fe4`. The final clamp changes only circle padding. The final APK was then installed with `adb install -r`, and its pulled `base.apk` matched SHA-256 `99B880B190440BAC59827AF79A59B76604B66894F9BD6A94E89F6900D389C05E`. The phone's existing progression document hash remained unchanged. Phone font size, portrait rotation and system locale were restored.

TalkBack was bound on the final APK's emulator. Native keyboard traversal visibly focused circle keys and horizontally revealed offscreen keys; the accessible tree included major/relative-minor descriptions and fretboard string/fret/pitch/degree descriptions. Spoken-output audibility was not measured. TalkBack, enlarged text, rotation and locale overrides were removed after inspection.

- [English Topics](screenshots/learning/topics-en.png)
- [TalkBack focus on a circle key at 120% text](screenshots/learning/talkback-circle-en.png)
- Korean landscape TAB at 200% text: [upper rows](screenshots/learning/strumming-ko-large-landscape.png) and [lower rows with aligned downstrokes after scrolling](screenshots/learning/strumming-ko-large-landscape-strokes.png).

Failed attempts remain in local task artifacts: the initial presenter return-marker collision, an offscreen G picker option, the obsolete Home assertion that Learning was unavailable, TAB typography lint warnings and overlapping circle targets. Each was corrected and rechecked. The final 120% test reproduced `Padding must be non-negative` before clamping the mathematically zero top/left coordinates against floating-point rounding. A subsequent verification command used a nonexistent JVM-domain Android test task; rerunning with `:domain:test` passed.

These are local results. CI, acoustic timing, physical touch performance and learner mastery have no additional claim here. Optional acoustic recording remains skipped.

## Content references

The text and examples are original; reference checks used Open Music Theory's [major scales](https://viva.pressbooks.pub/openmusictheory/chapter/major-scales/), [minor scales](https://viva.pressbooks.pub/openmusictheory/chapter/minor-scales/), [triads](https://viva.pressbooks.pub/openmusictheory/chapter/triads/), [seventh chords](https://viva.pressbooks.pub/openmusictheory/chapter/seventh-chords/) and [harmonic functions](https://open-musictheory.github.io/docs/harmony/harmonicFunctions/), plus Fender's [solo techniques](https://www.fender.com/articles/techniques/how-to-guitar-solo) and [palm muting](https://www.fender.com/articles/techniques/3-keys-to-ace-your-palm-muting).
