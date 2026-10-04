# Home menu verification

Verified on 2026-10-04 against [issue #8](https://github.com/pekochan069/GuitarLearner/issues/8), its [navigation contract](https://github.com/pekochan069/GuitarLearner/issues/8#issuecomment-5977091311), and the [confirmation](https://github.com/pekochan069/GuitarLearner/issues/8#issuecomment-5977103695).

## Behavior

Home lists usable features by category in two columns. Cards show a theme-tinted icon to the left of their title, with no Home introduction or card description. Initially it shows Tools and Metronome; empty Training and Learning categories are hidden. Up and Back return features to Home after dismissing the current overlay. Shared settings remain in the top app bar. Tuner and component examples have a separate development entry enabled only by the debug app capability.

One persistent presenter owns typed destinations and an exclusive settings/presets/overwrite overlay. Stable saved identifiers normalize unavailable destinations to Home. Home keeps its scroll state outside destination content. The app handles platform Back and notification input; rendering still receives contract state and emits events. No dependencies or audio behavior changed.

On other screens, the compact metronome displays the actual playing configuration rather than selected pending edits. Open does not start or reset playback. Stop waits for the reported playback state; a failed Stop retains the controller and exposes an error. Preparing, interrupted and failed states remain distinguishable, with access to the metronome even when no controller remains.

## Automated evidence for the navigation implementation

Checks used the committed dependency stack in an isolated worktree. The original checkout's IDE, dependency and wrapper edits were preserved.

```powershell
.\gradlew.bat verifyModuleBoundaries lint :architecture-lint:test :domain:test :adapters:testDebugUnitTest :presentation:logic:testDebugUnitTest :app:assembleDebug :app:assembleRelease --console=plain
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
pwsh -File scripts/verify-boundary-guard.ps1
```

The final static run passed: 71 local tests, zero failures or skips (architecture lint 20, domain 13, adapters 22, presenter 16), all product lint, module boundaries, and debug/release assembly. All three forbidden-dependency probes were rejected, and the temporary build-file changes were restored.

The first connected run had 43 passes, four notification fixture failures and one explicit acoustic skip. ActivityScenario compares launch action/categories with `Activity.getIntent()`, while notification consumption sanitizes that intent. Notification journeys now use native instrumentation activity launch and lifecycle monitoring to exercise the real intents. All nine focused notification, Back and pending-input tests passed after that change. Existing playback and underrun assertions remain intact.

Independent review found a process-restoration defect that ordinary activity recreation did not expose. A cold notification launch followed by Home, background process termination and Recents resume reopened Metronome. Android retains its original launch intent separately from the app's sanitized intent. Restored creation now restores only pending input and sanitizes the old action; a genuine new delivery still arrives through `onNewIntent`. The same process-death journey then retained Home, and a new notification on the killed retained task opened Metronome once with Home as its Back destination. Both paths were verified on API 37 with different process IDs before and after restoration.

The final complete API 37 connected run passed: JUnit XML reports 48 cases, 47 passes, zero failures and one opt-in acoustic skip. Coverage includes production Home visibility, unavailable destination restoration, retained Home scroll, actual/pending playback, failed Stop, truthful preparation, late preset completion, overlay Back order, locale/theme/sample restoration, grid/preset behavior, native playback and actual notification PendingIntent task/activity/service/media-session reuse.

## Installed UI and release evidence for the navigation implementation

The final release APK was signed locally with the debug key for installation; its application flags were not debuggable. Release settings had no development section. An explicit sample action and destination extra left Home unchanged. No build signing configuration changed.

Manual inspection used 360 × 800 dp and 800 × 360 dp on the API 37 emulator, English/dark and Korean/light views, normal and 200% system font. Home cards, compact controls and settings remained reachable through native scrolling. At 200% text, portrait compact actions wrap; landscape actions use 54 dp touch heights. The settings action has a 48 × 48 dp target. Home scroll survived opening a feature and returning with Up. System Back at Home followed normal Android background behavior while the metronome continued playing; returning retained Home and the actual configuration. Stop then removed the compact controller.

System Back dismissed an overwrite confirmation, then the preset sheet, then the feature, in that order. The draft remained available after reopening, and the temporary QA preset was deleted. Automated delayed-command tests cover completion after dismissal, while real UI inspection covers the native overlay stack.

After inspection, the debug APK was reinstalled. Device size/density, 100% font, rotation and automatic rotation were restored, along with Korean/light appearance and the previously visible 100 BPM, 8/8 selection. Playback was stopped. Raw XML, logs, design/review reports and signed local release evidence remain in `%TEMP%/GuitarLearner-home-8-20261004-201742`.

| Installed release view before the Home card revision | Evidence |
| --- | --- |
| English/dark, playing at 200% text | ![Actual configuration and wrapped actions](screenshots/home-menu/playing-en-large.png) |
| English/dark landscape, 200% text | ![Scrollable compact controller with reachable actions](screenshots/home-menu/playing-landscape-large.png) |
| Korean/light settings landscape, 200% text | ![Language options reached by scrolling](screenshots/home-menu/settings-landscape-large.png) |

## Follow-up Home card revision

The user requested a fixed two-column grid, removal of the Home heading and introductory text, removal of the card description, and the title to the right of the supplied Material Design Icons metronome icon. Each category now uses two equally weighted slots; the sole current feature occupies the first slot. The title wraps within its slot without truncation. The compact metronome still spans the available width.

The exact supplied SVG path is preserved in a 32 dp VectorDrawable with a 24-unit viewport and the existing theme tint. Its attribution and conversion notice remain in the source. The [upstream notice](https://github.com/Templarian/MaterialDesign/blob/master/LICENSE) and full Apache 2.0 text are bundled in `assets/licenses/material-design-icons.txt`; the debug APK was inspected to confirm that entry.

After the final icon/title revision, UI and app lint, `verifyModuleBoundaries`, debug/test APK assembly and release assembly passed. Four focused API 37 `FoundationPresentationTest` journeys passed: production Home with 200% text and half-width card bounds, retained Home scroll/restoration, actual playback with failed Stop, and truthful preparation controls. The earlier complete navigation suite above is baseline evidence; it was not rerun for this rendering-only revision. Instrumentation ran explicitly on `emulator-5554`, without running tests on the connected phone.

Installed debug inspection covered 360 × 800 dp in Korean and English with normal and 200% text, plus English at 800 × 360 dp with 200% text. All title text remained readable through wrapping. The final APK was also installed with `adb install -r` on the connected Samsung SM-S948N; its actual Home screen confirmed the removed text, two-column card width, supplied icon and title placement. Physical-device evidence is limited to installation, launch and Home appearance.

Emulator size/density, 100% font, rotation and automatic rotation were restored. Korean/light appearance was restored; this revision inspection did not edit metronome controls or start playback. Build logs, focused instrumentation output and raw device captures remain in `%TEMP%/GuitarLearner-home-8-20261004-201742` under `home-icon-title-*`.

| Final installed debug view | Evidence |
| --- | --- |
| Korean/light Home | ![Two-column Home with icon beside title](screenshots/home-menu/home-ko-light.png) |
| Korean/light Home, 200% text | ![Wrapping Korean title beside icon](screenshots/home-menu/home-ko-large.png) |
| English/light Home, 200% text | ![Wrapping English title beside icon](screenshots/home-menu/home-en-large.png) |
| English/light landscape, 200% text | ![Two-column Home in landscape](screenshots/home-menu/home-en-landscape-large.png) |

## Review

Three independent design lanes selected typed destinations in one presenter; the synthesis adopted an exclusive overlay state and app-owned platform input. Independent comment and correctness reviews plus parent review covered the diff. All lanes used the configured Codex model, with no model-family diversity.

Model the Domain shaped destinations and overlays. Laziness Protocol kept one presenter and existing audio ownership. Separate Before Serializing Shared State kept implementation and device ownership distinct. Test Behavior and Prove It Works required real task, notification, release and UI journeys. The initial code and evidence form separate verifiable commits.

Physical-device playback/navigation beyond Home appearance, pre-API-33 locale behavior and spoken TalkBack output remain unverified. Accessibility evidence covers labels, semantics, touch bounds and manual screen inspection. Acoustic recording is opt-in and skipped; this change makes no hardware timing claim.
