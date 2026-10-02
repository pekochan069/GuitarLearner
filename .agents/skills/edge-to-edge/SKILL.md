---
name: edge-to-edge
description: >
  Implement or debug Jetpack Compose edge-to-edge behavior: system-bar overlap,
  list/FAB insets, IME avoidance, system-bar legibility, adaptive scaffolds, or
  full-screen dialogs.
license: Complete terms in LICENSE.txt
metadata:
  author: Google LLC
  last-updated: '2026-04-01'
  keywords: [android, compose, system bars, edge-to-edge, status bar, navigation bar, IME]
---

# Compose Edge to Edge

For diagnosis or planning, inspect and report. For an explicit fix or migration, make the requested local changes and run non-destructive validation. A localized inset fix does not authorize a target-SDK or all-Activity migration; confirm material scope expansion.

## 1. Recon

Inspect target SDK, Activity/window setup, theme, scaffold ownership, adaptive containers, scrollables, FABs, text inputs, dialogs, and existing inset consumption. Determine whether edge-to-edge is already enforced or enabled and which ancestor owns each inset.

Recon is complete when every affected system-bar or IME inset has one identified owner and duplicated/missing consumption is located.

## 2. Route

| Problem | Load |
| --- | --- |
| status/navigation overlap, lists, FABs, scaffolds, adaptive layouts | [system insets](references/insets.md) |
| keyboard obscures fields or creates excess padding | [IME](references/ime.md) |
| icon contrast, navigation-bar scrim, status-bar protection | [system-bar legibility](references/system-bars.md) |
| full-screen Compose dialog | [dialogs](references/dialogs.md) |

Use one inset strategy per boundary. Material components may already consume their own insets; verify before adding padding.

## 3. Implement

- Apply the smallest fix in the affected Activity/screen.
- Add or retain `enableEdgeToEdge()` when required by the current Activity setup and requested migration scope.
- Add `adjustResize` only for Activities whose keyboard behavior requires it and whose current configuration does not already provide equivalent behavior.
- Pass list insets through `contentPadding` so content can draw behind bars while first/last items remain reachable.
- Keep decorative backgrounds edge-to-edge while moving interactive/readable content into safe bounds.

## 4. Verify

Test affected screens in gesture and three-button navigation, light/dark theme, portrait/landscape when supported, and keyboard open/closed. Confirm first/last list items, FABs, focused inputs, and system-bar icons remain reachable and legible. Run the smallest relevant compile/test task.

Complete when the project builds, each affected inset has one owner, no critical content is obscured or double-padded, keyboard transitions preserve the focused field, and performed checks are reported as pass/fail/not-run.
