---
name: find-animation-opportunities
description: Inspect unchanged Compose UI for missing motion and return a read-only opportunity report. Use only when asked what should start animating. For existing-motion defects use improve-animations; for changed code use review-animations.
---

# Finding Compose Animation Opportunities

Search for missing motion, then reject most candidates. Produce at most `5–7` high-confidence suggestions for an app and fewer for one screen. Never modify source.

## Gate

Every suggestion must pass all four questions.

### 1. Frequency

| Frequency | Verdict |
| --- | --- |
| 100+ times/day, keyboard navigation, core practice loop | Reject decorative transitions and added latency; preserve immediate functional visualization, direct manipulation, state feedback, and platform indication |
| Tens/day | Reject or use only platform-default/near-imperceptible feedback |
| Occasional | Standard motion eligible |
| Rare / first-time | Delight eligible |

### 2. Purpose

Name one: **feedback**, **spatial continuity**, **state indication**, **preventing a jarring change**, **explanation**, or **delight**. Delight is limited to rare moments.

### 3. Budget

Recurring UI motion usually stays below `300ms`. Sheets, navigation, explanatory motion, or spring settling may run longer when justified. Stagger uses `30–80ms` between items and never blocks interaction.

### 4. Function

Motion must not delay input, move readable content decoratively, obscure focus, or become the only state signal.

## Recon

1. Read `gradle/libs.versions.toml` and module build files. Record Compose/Material versions and supported targets.
2. Derive active targets and source sets from Gradle; never infer them from directory names. Prefer common APIs for shared behavior.
3. Inventory existing animation APIs, Material components, `MaterialTheme.motionScheme`, and shared specs.
4. Build a frequency map across touch, keyboard, mouse, and accessibility paths.
5. Treat Material component motion and indication as existing motion; do not duplicate it.

Useful searches:

```text
AnimatedVisibility|AnimatedContent|Crossfade|animate.*AsState
updateTransition|rememberTransition|Animatable|animateContentSize
animateBounds|animateItem|rememberInfiniteTransition|graphicsLayer
pointerInput|draggable|anchoredDraggable|SwipeToDismiss|SharedTransitionLayout
PredictiveBackHandler|if \(.*\)|when \(
```

## Where to hunt

### Teleporting state

- Conditional content that appears or disappears without continuity: consider `AnimatedVisibility`.
- Content swaps that are hard to follow: consider `AnimatedContent` or `Crossfade`.
- Expanding content that moves siblings abruptly: prefer `animateContentSize`; use `animateBounds` only when a stable `LookaheadScope` owner and coordinate space are identified.
- Keyed lazy items that snap during insert, removal, or reorder: consider `animateItem()`.

Use partial scale explicitly when scale is chosen:

```kotlin
enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
    scaleIn(
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        initialScale = 0.95f,
    )
```

### Missing spatial story

- A custom menu, popup, or contextual surface disconnected from its trigger: consider trigger-relative `TransformOrigin`.
- Navigation where forward and back feel identical despite a spatial hierarchy: consider direction-aware transitions.
- Content preserving identity between screens: consider `SharedTransitionLayout` or shared bounds.
- Android back navigation that jumps instead of following gesture progress: consider predictive back in `androidMain`.

### Feedback gaps

First verify Material indication, semantics, focus, and state styling. Suggest custom scale, color, or shape motion only when baseline feedback is genuinely insufficient. Frequent buttons normally need no extra motion.

### Gestures

- Drag release ignores velocity: use Material drag components or `Animatable.animateDecay`.
- Motion cannot reverse cleanly: use state-driven animation or `Animatable`, not fixed choreography.
- Boundary stops feel rigid: add increasing resistance.
- Swipe/reorder has no placement continuity: use suitable Material component or lazy-layout item animation.

### Rare moments

Onboarding, first completed lesson, streak milestone, and empty-to-populated state may spend a small delight budget. Reject ambient decoration in core learning flow.

## Exact recipes

Every surviving suggestion must include:

- `file:line` evidence;
- source set (`commonMain` or platform-specific);
- exact Compose API and `AnimationSpec`;
- whether Material already supplies motion;
- reduced-motion behavior;
- one mechanical check and one feel check.

Prefer existing Material motion specs. If project needs explicit custom curves, use exact values:

```kotlin
CubicBezierEasing(0.23f, 1f, 0.32f, 1f) // strong deceleration
CubicBezierEasing(0.77f, 0f, 0.175f, 1f) // strong accelerate/decelerate
CubicBezierEasing(0.32f, 0.72f, 0f, 1f) // drawer-like settle
```

Do not add a dependency for built-in Compose capability. Do not put Android-only APIs in `commonMain`.

## Workflow

1. **Recon.** Done when stack, targets, source sets, existing specs, Material-owned motion, and frequency map are recorded.
2. **Sweep.** Check every hunt category; retain `file:line` evidence for candidates.
3. **Gate.** Record why each candidate survives or fails.
4. **Report.** If none survive, say so.

## Output

### Opportunities

| # | Location | Today | Purpose / frequency | Suggested motion |
| --- | --- | --- | --- | --- |
| 1 | `shared/src/commonMain/.../Lesson.kt:41` | Result content appears through `if` | State indication / occasional | `AnimatedVisibility`; `fadeIn(fastEffectsSpec()) + scaleIn(fastSpatialSpec(), initialScale = 0.95f)`; matching exit; `commonMain`; verify animator scale `0x` reaches final state |

### Rejected candidates

List `2–5` candidates and gate reason:

- `shared/src/commonMain/.../Fretboard.kt:88` — decorative scale on every note highlight. **Rejected: core loop, 100+/day; preserve immediate note-state visualization but remove decorative entrance motion.**

### Evidence

- files and source sets inspected;
- dependency and target evidence used;
- commands run with pass/fail/not-run;
- feel and accessibility checks performed or explicitly not performed;
- residual risks or unverified behavior.

### Verdict

One paragraph: actual motion need, highest-leverage suggestion, and uncertainty that requires running UI. Point selected rows to `improve-animations plan <suggestion>`.

Complete when every in-scope hunt category was checked, every suggestion passed all four gates with `file:line` evidence, and performed versus proposed checks are distinct.
