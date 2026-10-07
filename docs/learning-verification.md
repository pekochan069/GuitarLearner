# Learning verification

The implementation follows [issue #36](https://github.com/pekochan069/GuitarLearner/issues/36), resolved decisions #33, #34 and #35, and the [approved navigation revision](https://github.com/pekochan069/GuitarLearner/issues/36#issuecomment-6037798326). Home has direct Topics and Courses entries. Interactive exploration belongs inside theory lessons. All six theory lessons and seven technique lessons remain available.

Courses show a goal, shared completion and the first incomplete lesson. A completed course offers Review. Each lesson has an original Korean and English explanation and an observable practice goal. Completion records the learner's choice to finish a lesson, not a mastery assessment.

Home menu cards use existing Material 3 color pairs. Tools use `secondaryContainer/onSecondaryContainer`, Training uses `tertiaryContainer/onTertiaryContainer`, and Learning uses `surfaceContainerHigh/onSurface`. Chords uses the same pair as the other Tools. Training menu cards also use the Training pair.

## Acceptance evidence

| Behavior | Evidence |
| --- | --- |
| Enter Topics or Courses directly | `FoundationPresentationTest` checks both Home entries. `LearningJourneyTest` enters each through the installed app and checks the course overview. |
| Share progress and recommend the next lesson | Native journeys complete a lesson through Topics, continue through its course, and complete all six theory lessons to reach Review. Presenter checks read live completion when Continue is pressed. Every lesson remains unlocked. |
| Keep course context through Back and linked destinations | Native journeys check course overview navigation, Metronome, Progressions and active Training returns across Activity recreation. Presenter checks cover selections, instruments, notification reopening and restored pages. |
| Read and practice the curriculum | Both languages have 13 goals and revised explanations. The seven technique practice tasks and whitespace-preserving TAB examples remain. `LearningLayoutTest` checks enlarged text, goals, overview actions and compiled TAB markers. |
| Explore and play musical relationships | Domain fixtures and native journeys check contextual spelling, scales, chords and progressions. Both Piano and Guitar examples reach native Playing and finish. Navigation and background departure stop playback. Learning and Progressions interrupt one another through native audio focus. |
| Keep progress across failures and cold launch | Adapter and presenter tests cover failed writes, optimistic completion and retry. With no active default network, a manual force-stop and cold launch changed PID 17667 to 18330. The theory course restored 1/6 completion and recommended Note names and intervals despite Circle of fifths being last viewed. |
| Distinguish menu categories in both themes | The final APK's English emulator and Korean phone screens were inspected in light and dark themes. All four Tools share one color. Training and Learning each have a distinct color. |

## Final checks and APK

These commands passed for the final source:

```text
gradlew verifyModuleBoundaries lint :architecture-lint:test :domain:test :adapters:testDebugUnitTest :presentation:logic:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

The unit results contain 243 cases: 242 passes, zero failures or errors, and one existing recorded-guitar fixture skip. Architecture-lint accounts for 21 of these cases.

The isolated API 35 Google APIs x86_64 emulator used a 360 × 800 viewport, density 160 and disabled animations. The final affected native run passed all 35 cases:

- `FoundationPresentationTest`: 21.
- `LearningJourneyTest`: 4.
- `LearningLayoutTest`: 1.
- `TrainingLayoutTest`: 7.
- The exact Progression snackbar-expiry check: 1.
- The exact Learning/Progression native-focus check: 1.

The final debug APK SHA-256 is `0401C1E72C558E24A814FF3521E3B61EF7A16FBE75C292466DD519F8D2DC29F6`. After USB reconnection, `adb install -r` succeeded on Samsung SM_S948N. Its pulled `base.apk` matched that hash. `MainActivity` was launched and verified in the foreground. The final phone inspection covered both Home themes and category colors.

Before the color change, APK `B0A18CB0E937BDDD215C9C5956F76AE9C9CC082FF71143162D343A7E6F8405DE` was inspected on the same phone. It covered course entry, overview, lesson Back, and Korean goals/actions at 200% text in actual 2340 × 1080 landscape. These geometry checks were not repeated on the final color APK.

The phone's Learning and Progressions files remained byte-identical after installation and inspection. Their SHA-256 values were `C8D5E76DCA6463D35CD2BC359CDA1008844AB4F5D75955FE42158BD08987CC24` and `DAC51609511FD3BDE624EAECACC37CF2CF78D2EDD6CEC115913017C3DEAECF3B`. Phone font size, portrait rotation, system theme and empty app-locale override were restored. Emulator accessibility, network and theme overrides were restored.

TalkBack bound on the final emulator and its accessible tree contained the course goal, completion and Continue label. Final keyboard accessibility focus and spoken-output audibility were not confirmed. Earlier circle-focus and TAB screenshots below are historical checks from before this navigation revision.

## Failed attempts and limits

The first full native run contained 99 cases: 93 passes, five failures and one optional acoustic-recording skip. The failures were Home notification reopening, Metronome signature restart, two Progression audio-focus checks, and Progression snackbar expiry. An exact rerun passed the four Home/audio checks. The snackbar check failed again.

The snackbar test waited on wall-clock time for a Compose-controlled delay. The test now advances the Compose clock by the existing 4,000 ms duration and waits for idle. Production snackbar behavior is unchanged. The [AndroidX clock contract](https://android.googlesource.com/platform/frameworks/support/+/81327c1161662d461dd4faea6a2bcca448c3db6c/compose/ui/ui-test/src/commonMain/kotlin/androidx/compose/ui/test/MainTestClock.kt) documents delayed effects on that clock. The corrected check passed in the final 35-case run. The full 99-case suite was not repeated.

The first static run rejected the English progress counter with `PluralsCandidate`. Rephrasing it to `Lessons completed: %1$d / %2$d` passed lint without suppression. A manual UI dump immediately after cold launch had a null root. Inspection resumed after the actual app window appeared, and the task helper now rejects failed dumps instead of returning stale XML.

Logs and XML from the failed full run, exact rerun and final run remain in the local `guitarlearner-learning-flow` task artifacts. These are local results. CI, acoustic timing, physical touch performance and learner mastery have no additional claim here.

## Screenshots

- Final Korean phone Home: [light](screenshots/learning/home-ko.png) and [dark](screenshots/learning/home-ko-dark.png).
- Final English emulator: [Topics](screenshots/learning/topics-en.png) and [course overview after cold launch](screenshots/learning/course-overview-en.png).
- Revised lesson goal on the earlier phone APK at [200% Korean landscape](screenshots/learning/lesson-goal-ko-large-landscape.png).
- Historical circle [TalkBack focus](screenshots/learning/talkback-circle-en.png), and technique TAB [upper rows](screenshots/learning/strumming-ko-large-landscape.png) and [lower rows](screenshots/learning/strumming-ko-large-landscape-strokes.png).

## Content references

The text and examples are original. Reference checks used Open Music Theory's [major scales](https://viva.pressbooks.pub/openmusictheory/chapter/major-scales/), [minor scales](https://viva.pressbooks.pub/openmusictheory/chapter/minor-scales/), [triads](https://viva.pressbooks.pub/openmusictheory/chapter/triads/), [seventh chords](https://viva.pressbooks.pub/openmusictheory/chapter/seventh-chords/) and [harmonic functions](https://open-musictheory.github.io/docs/harmony/harmonicFunctions/), plus Fender's [solo techniques](https://www.fender.com/articles/techniques/how-to-guitar-solo) and [palm muting](https://www.fender.com/articles/techniques/3-keys-to-ace-your-palm-muting).
