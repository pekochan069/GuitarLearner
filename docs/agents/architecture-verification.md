# Architecture verification

The project dependency rules live in `build.gradle.kts`. `verifyModuleBoundaries` checks declarations inherited by resolvable production compile/runtime classpaths, including variant API buckets and transitive project dependencies. Test classpaths are excluded. Missing module graphs, unknown declaration types, local files, and unresolved edges fail the gate. Direct dependency allowlists keep domain, contract, presenter logic, and rendering narrow; separate resolved allowlists admit the verified Circuit/Compose runtime support libraries. `check` depends on the gate.

Every product module runs the custom checks from `:architecture-lint`. Domain uses standalone Android Lint on Kotlin/JVM. The Android modules use their normal lint tasks. Architecture findings fail lint; no baseline suppresses them. The detector tests describe the supported source forms.

The compiler and library versions remain pinned to the verified stack. The app keeps the built-in `AndroidGradlePluginVersion`, `GradleDependency`, and `NewerVersionAvailable` update advisories visible as information. Correctness and architecture warnings remain errors. The manifest marks the API 33 locale configuration; AppCompat supplies locale persistence on earlier supported Android versions.

Run the verification tasks from the repository root:

```powershell
.\gradlew.bat verifyModuleBoundaries lint :architecture-lint:test :adapters:testDebugUnitTest :presentation:logic:testDebugUnitTest :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest
pwsh scripts/verify-boundary-guard.ps1
```

The API checks use resolved symbols and types, including supported direct calls in helpers and getters. The Either check covers Kotlin calls, Unit-coerced lambda results, and unread or statement-only local aliases. It treats a productive consumer use as handled; it does not prove correct error policy inside that consumer. It does not analyze Java Either results, discarded property reads, mutable alias paths, or interprocedural control flow. The checks do not prove third-party purity or follow arbitrary reflection. Dependency allowlists and behavioral tests cover those remaining limits. A new effect API needs a detector fixture and a reviewed dependency decision.

Connected tests preserve the localized theme/locale recreation journey, all six saved sample values, and the typed save-failure UI. Adapter tests cover checked writes, recovery, and cancellation. Presenter tests cover typed failure, repeated taps, sample events, and externally observed settings. AppCompat remains the locale persistence authority; acceptance of a locale request is not a disk-write acknowledgment.
