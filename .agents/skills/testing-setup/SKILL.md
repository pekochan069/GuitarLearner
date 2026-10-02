---
name: testing-setup
description: >
  Analyze or add a requested Android testing layer: local unit, Compose/View UI,
  screenshot, database, instrumented, end-to-end, or coverage. Use for testing
  strategy and test-infrastructure setup while preserving the project's existing
  stack.
license: Complete terms in LICENSE.txt
metadata:
  author: Google LLC
  last-updated: '2026-06-25'
  keywords: [android, testing, ui tests, screenshot tests, coverage]
---

# Android Testing Setup

## Authorization

- **Analyze or plan:** inspect the project and report; keep files unchanged.
- **Add or set up:** make the requested local testing changes and run relevant non-destructive checks.
- Confirm before introducing dependency injection, replacing a test framework, adding device/cloud services, or materially expanding beyond the requested test layer.

## 1. Recon

Inspect version catalogs, module build files, source sets, existing tests, CI, and test tasks. Record:

- Android/Compose/KMP targets;
- existing unit, UI, screenshot, database, instrumented, E2E, mocking, DI, and coverage tools;
- whether UI is Compose, Views, or hybrid;
- commands that already run each layer.

Recon is complete when every requested layer is classified as present, partial, or absent with file/task evidence.

## 2. Route the request

| Requested outcome | Minimal route |
| --- | --- |
| Analysis or strategy | Report current layers, gaps tied to the goal, and smallest next step |
| Unit tests | Reuse current runner; add tests only for named business logic |
| Compose UI tests | Use Compose test APIs and semantic matchers; choose `test` or `androidTest` from actual environment needs |
| View UI tests | Reuse Espresso or the installed wrapper |
| Screenshot tests | Choose one installed or explicitly requested local/device framework; load [Compose screenshot setup](references/android/studio/preview/compose-screenshot-testing.md) when applicable |
| Database tests | Use the real database engine with isolated/in-memory storage where supported |
| Device/system UI journey | Use existing instrumentation; add UI Automator only when platform UI is part of the assertion |
| Coverage | Add/configure coverage only when requested |
| Hilt instrumented tests | Load [Hilt testing](references/android/training/dependency-injection/hilt-testing.md) |
| Compose configuration matrix | Load [Compose UI testing patterns](references/android/develop/ui/compose/testing/common-patterns.md) |

Preserve the current stack. Add only dependencies required by the selected route. A missing DI framework is not a testing defect; introduce DI only when runtime substitution is required and the user approves the architectural expansion. Prefer fakes at existing seams; add an interface only when the requested test cannot control a dependency otherwise. Mock only when a fake is impractical.

## 3. Implement

Keep the change bounded to the selected layer:

- use project naming, source sets, runners, and Gradle conventions;
- test requested behavior and material edge cases rather than every file;
- use semantics before `testTag` in Compose; add tags when semantics cannot identify the node clearly;
- choose a representative screenshot matrix from supported layouts, themes, font scales, and UI states instead of a fixed Cartesian product;
- keep E2E journeys few and high-value;
- document test commands only when the request includes documentation.

## 4. Validate

Run the smallest relevant Gradle tasks, then any broader task required by changed build configuration. Report exact commands and pass/fail/not-run status. Device-only checks remain explicitly unverified when no device is available.

## Completion

Only requested layers are configured, representative tests pass, changed build files resolve, commands/results are reported, and remaining device, emulator, baseline-image, or CI constraints are named.
