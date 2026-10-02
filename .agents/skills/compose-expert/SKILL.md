---
name: compose-expert
description: Implement, debug, explain, or review general Jetpack Compose and Compose Multiplatform code involving state, effects, modifiers, lists, navigation, accessibility, performance, resources, platform interop, Paging, or TV. Use material-3 for design-system work and edge-to-edge for system inset problems.
version: 2.5.0
---

# Compose Expert

For answer, explanation, review, diagnosis, or plan requests, inspect relevant code and report. For build, fix, or change requests, make requested in-scope local edits and run relevant non-destructive validation. Confirm before external writes, destructive actions, dependency/tool installation, or material scope expansion.

## Route first

Read only the reference matching the request. Add a secondary reference only when the task crosses that boundary. Route Material design-system/audit work to `material-3`, system-inset problems to `edge-to-edge`, and specialized motion discovery/audit/review to the corresponding animation skill.

| Signal | Reference |
| --- | --- |
| `remember`, state hoisting, `derivedStateOf`, `snapshotFlow`, StateFlow | [state management](references/state-management.md) |
| `LaunchedEffect`, `DisposableEffect`, `SideEffect`, coroutine scopes | [side effects](references/side-effects.md) |
| `CompositionLocal`, ambient values, theme propagation | [composition locals](references/composition-locals.md) |
| recomposition, stability, compiler metrics, baseline profiles | [performance](references/performance.md) |
| modifier order, custom layout/draw, `Modifier.Node`, `graphicsLayer` | [modifiers](references/modifiers.md) |
| lazy lists, grids, pager, keys, content types, scrolling | [lists and scrolling](references/lists-scrolling.md) |
| `AnimatedVisibility`, transitions, `Animatable`, shared elements | [animation](references/animation.md) |
| Nav 2, typed routes, deep links | [navigation](references/navigation.md) |
| Nav 3 or Nav 2 migration | [navigation migration](references/navigation-migration.md) |
| Paging setup and `LazyPagingItems` | [Paging](references/paging.md) |
| offline-first `RemoteMediator` | [offline Paging](references/paging-offline.md) |
| Paging tests or MVI | [Paging MVI/testing](references/paging-mvi-testing.md) |
| reusable design-system hierarchy or component library | [atomic design](references/atomic-design.md) |
| Figma, screenshot, redline, or visual spec implementation | [design to Compose](references/design-to-compose.md) |
| experimental Styles API | [styles](references/styles-experimental.md) |
| Compose Multiplatform source sets, resources, `expect`/`actual` | [multiplatform](references/multiplatform.md) |
| desktop/iOS/web entry points or native interop | [platform specifics](references/platform-specifics.md) |
| TV, D-pad, focus, 10-foot UI | [TV Compose](references/tv-compose.md) |
| Views/Compose interop | [view composition](references/view-composition.md) |
| semantics, TalkBack, focus order, touch targets | [accessibility](references/accessibility.md) |
| removed API or version migration | [deprecated patterns](references/deprecated-patterns.md) |
| production crash, ANR, duplicate key, stale state | [crash playbook](references/production-crash-playbook.md) |
| explicit GitHub PR URL | [PR review](references/pr-review.md) |

For local review requests, inspect the supplied diff/files directly and load topic references as needed. For reusable component-library work, load atomic-design guidance; ordinary composables do not require atom/molecule classification.

## Workflow

1. Resolve requested outcome, affected Compose layer, active Gradle targets/source sets, installed versions, and approval boundary.
2. Load the matched reference and inspect relevant code/callers.
3. Answer or make the smallest requested change using project conventions and APIs available at installed versions.
4. Validate changed targets with the smallest relevant Gradle tasks and behavior checks.
5. Report result, evidence, material caveats, and remaining unverified behavior.

Completion requires the requested scope addressed, matched API/version checked, relevant validation recorded as pass/fail/not-run, and unresolved assumptions stated.

## Durable principles

- Compose work flows through composition, layout, and draw; read state in the latest phase that can produce the intended behavior.
- Hoist state only as high as ownership requires.
- Modifier order changes behavior and appearance.
- Side effects bridge declarative state to imperative APIs; choose by lifecycle.
- Shared runtime does not make platform APIs portable; derive active targets from Gradle and keep platform APIs in matching source sets.
- Prefer stable inputs and avoid avoidable work in composable bodies; establish performance with compiler/runtime evidence rather than folklore.

## Source receipts

When internals materially affect the answer or the user asks for implementation evidence, load the topic reference first, then the matching receipt:

| Internals | Receipt |
| --- | --- |
| runtime, snapshots, effects, remember | [runtime source](references/source-code/runtime-source.md) |
| UI, layout, measurement, draw | [UI source](references/source-code/ui-source.md) |
| lazy lists, pager, clickable, scrolling | [foundation source](references/source-code/foundation-source.md) |
| Material 3 components | [Material source](references/source-code/material3-source.md) |
| Navigation Compose | [navigation source](references/source-code/navigation-source.md) |
| Compose Multiplatform entry points | [CMP source](references/source-code/cmp-source.md) |

Cite exact repository paths only when receipts are relevant.
