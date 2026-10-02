---
name: android-cli
description: Use the `android` CLI to create, inspect, run, and test Android projects; manage SDKs and emulators; inspect device UI; capture screens; search official Android docs; or manage Android skills.
license: Complete terms in LICENSE.txt
metadata:
  author: Google LLC
  keywords: [sdk, emulator, skills, docs, project creation, screenshots]
---

# Android CLI

Use installed command help as the syntax source of truth:

```bash
android --version
android help [COMMAND]
```

For explanation, review, or planning requests, inspect and report. For build, fix, or setup requests, make only the requested local changes and run relevant non-destructive checks. Obtain confirmation before downloading or executing an installer, deleting an emulator or SDK package, or materially expanding scope.

## Route

| Goal | Command or reference |
| --- | --- |
| Inspect environment | `android info` |
| Create a project | `android create --list`, then `android create …` |
| Inspect project outputs | `android describe --project_dir=<path>` |
| Search current Android guidance | `android docs search …`; fetch the selected result |
| Install/list SDK packages | `android sdk …` |
| Create/start/stop/list emulators | `android emulator …` |
| Deploy an APK | `android run …` |
| Inspect a running UI | `android layout …`; load [device interaction](references/interact.md) |
| Capture a device screen | `android screen capture -o <path>` |
| Resolve a visual target | `android screen resolve …`; load [device interaction](references/interact.md) |
| Run a device journey | load [journey execution](references/journeys.md) |
| Manage Android skills | `android skills …` |
| Update the CLI | `android update` |

Use `android docs` for current Android API, migration, and best-practice questions instead of relying on cached command examples.

## Installation

When an authorized task requires the missing CLI, show the platform command and ask before running it:

```text
Linux:       curl -fsSL https://dl.google.com/android/cli/latest/linux_x86_64/install.sh | bash
macOS Arm:   curl -fsSL https://dl.google.com/android/cli/latest/darwin_arm64/install.sh | bash
macOS Intel: curl -fsSL https://dl.google.com/android/cli/latest/darwin_x86_64/install.sh | bash
Windows:     winget install Google.AndroidCLI
```

## Completion

The requested command succeeds, its expected project/device state is observed, and the response records commands, results, and any unresolved device or installation constraint.
