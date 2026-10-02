# Compose Multiplatform Motion Standards

Use installed Compose and Material versions as source of truth. Material component motion is presumed intentional until evidence shows otherwise.

## Purpose and frequency

Valid purposes: feedback, spatial continuity, state indication, explanation, and prevention of a jarring change.

| Frequency | Standard |
| --- | --- |
| 100+ times/day, keyboard path, core practice loop | Reject decorative transitions and added latency; preserve immediate functional visualization, direct manipulation, state feedback, and platform indication |
| Tens/day | Short and subtle |
| Occasional | Standard motion |
| Rare / first-time | Expressive motion allowed |

Motion never delays input or becomes the only state signal.

## Specifications

Preference order:

1. Material component default.
2. `MaterialTheme.motionScheme`.
3. Existing project spec.
4. New custom spec with behavior-specific reason.

```kotlin
MaterialTheme.motionScheme.fastSpatialSpec<Float>()
MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
MaterialTheme.motionScheme.slowSpatialSpec<Float>()
MaterialTheme.motionScheme.fastEffectsSpec<Float>()
MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
MaterialTheme.motionScheme.slowEffectsSpec<Float>()
```

Explicit curves when custom behavior is justified:

```kotlin
CubicBezierEasing(0.23f, 1f, 0.32f, 1f) // strong deceleration
CubicBezierEasing(0.77f, 0f, 0.175f, 1f) // accelerate/decelerate
CubicBezierEasing(0.32f, 0.72f, 0f, 1f) // drawer-like settle
```

Recurring duration-based UI motion normally stays below `300ms`. Longer sheets, navigation, explanations, and spring settling require contextual judgment, not automatic rejection. Acceleration curves may suit departure; default Material easings are not findings by themselves.

## Spatial continuity

Compose scale transitions default to zero. Prefer explicit partial scale when scale is used:

```kotlin
scaleIn(
    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    initialScale = 0.95f,
    transformOrigin = TransformOrigin.Center,
)
```

Trigger-anchored surfaces use trigger-relative `TransformOrigin`; centered dialogs remain centered. Preserve shared identity through shared elements/bounds when benefit exceeds complexity. Direction-aware navigation should match hierarchy.

## API choice and interruption

| Need | Compose tool |
| --- | --- |
| One state-driven value | `animate*AsState` |
| Coordinated values | `updateTransition` |
| Enter/exit | `AnimatedVisibility` |
| Content replacement | `AnimatedContent` / `Crossfade` |
| Local size or item placement | `animateContentSize`, `animateItem` |
| Complex bounds in stable lookahead coordinates | `animateBounds` inside a stable `LookaheadScope`; identify scope owner and coordinate space |
| Imperative or gesture value | remembered `Animatable` |
| Velocity continuation | `animateDecay` |
| Fixed choreography | `keyframes` |

Rapidly reversible state retargets from current value. Gesture release preserves velocity where physical continuity requires it. Material drag/swipe/sheet components outrank custom pointer code.

## Compose phases

Use latest phase capable of intended behavior:

```kotlin
// Visual-only movement: draw phase
Modifier.graphicsLayer { translationX = animatedPx }

// Placement must change: layout phase
Modifier.offset { IntOffset(animatedPx.roundToInt(), 0) }

// Surrounding content must move: layout animation
Modifier.animateContentSize()

// Complex bounds: stable LookaheadScope owner required
with(lookaheadScope) {
    Modifier.animateBounds(lookaheadScope = this)
}
```

Flag composition-time reads only when later-phase read produces identical behavior. Layout animation is correct when siblings must follow. Inspect unstable state, snapshot writes, allocations, recomposition, measurement, drawing, and layer cost.

Performance findings cite evidence. HIGH severity jank needs reproducible frame misses or trace data, not generic “GPU” claims. Flag animated state mutation during composition, avoidable per-frame allocation, and `Animatable` created without `remember`.

## Gestures

- Direct manipulation follows pointer through `snapTo` or Material gesture state.
- Release uses density-aware velocity and positional thresholds or component defaults.
- Cancellation settles quickly.
- Bounds use resistance rather than abrupt stopping when appropriate.
- Active pointer stays stable; extra pointers do not cause jumps.
- Physical devices validate touch feel; desktop validates mouse and keyboard.

## Accessibility and semantics

- Android animator duration scale `0x` reaches final state.
- iOS Reduce Motion is tested only when Gradle declares an iOS target and affected code exists.
- Desktop motion preference follows explicit product policy when no portable signal exists.
- Focus order/restoration, click targets, semantics, and announcements survive transitions.
- Motion never carries information alone.
- Reduced motion removes nonessential travel, parallax, bounce, flashing, and ambient loops while retaining useful state/color/alpha feedback.
- Infinite motion has purpose, lifecycle control, and reduced-motion behavior.

## Source sets

- Derive active targets and source sets from Gradle; never infer them from directory names.
- Shared behavior uses APIs available to `commonMain` at the installed version.
- Platform APIs and policy adapters stay in matching active source sets.
- Plans and reviews name experimental APIs and required opt-ins.

## Cohesion

Lazy item motion requires stable item keys. Use standard Material motion for recurring learning UI. Expressive motion belongs to rare milestones or playful surfaces. Repeated custom values may become shared specs after repetition exists; one-off values do not justify abstraction. Stagger uses `30–80ms`, remains interruptible, and never delays interaction.

## Severity

- **HIGH** — blocked input, broken focus/semantics, harmful infinite motion, wrong-source-set API, severe measured jank.
- **MEDIUM** — incorrect phase, non-interruptible reversible gesture, zero-scale entrance, missing reduced-motion validation, broken spatial story.
- **LOW** — duplicate arbitrary specs, small duration mismatch, optional polish.

## Approval checklist

Approve only when:

- purpose and frequency justify motion;
- Material defaults or motion scheme are reused where suitable;
- API and source set match installed dependencies;
- reversible interaction remains interruptible;
- zero/reduced duration preserves final state and semantics;
- performance claims have evidence;
- relevant compile, test, and manual feel checks are identified.
