---
name: emil-design-eng
description: Guide implementation of a specific Compose Multiplatform UI interaction or polish question using restrained component craft. Use for design or build guidance. For missing-motion discovery use find-animation-opportunities; for repository-wide motion audits use improve-animations; for changed animation code use review-animations.
---

# Compose Design Engineering

Taste is restraint plus invisible correctness. Motion earns its place through feedback, spatial continuity, state indication, explanation, or prevention of a jarring change.

## Decide

### Frequency

| Frequency | Motion budget |
| --- | --- |
| Core loop, keyboard path, 100+ uses/day | Immediate functional visualization, direct manipulation, state feedback, or Material indication; no decorative transition or latency |
| Tens/day | Short and subtle |
| Occasional | Standard motion |
| Rare onboarding or milestone | Delight allowed |

### Mechanism

1. Keep suitable Material component motion and indication.
2. Reuse `MaterialTheme.motionScheme`.
3. Use `animate*AsState` for one state-driven value and `updateTransition` for coordinated values.
4. Use `AnimatedVisibility`, `AnimatedContent`, or `Crossfade` for structural state.
5. Use `animateContentSize` or `animateItem` when surrounding layout follows.
6. Use `animateBounds` only inside a stable `LookaheadScope`; identify its owner and coordinate space.
7. Use remembered `Animatable`, `animateDecay`, or Material gesture components for direct manipulation and velocity.
8. Use `keyframes` only for fixed choreography users do not reverse.

Derive active targets and source sets from Gradle; never infer them from directory names. Prefer common APIs for shared behavior and keep platform APIs in active platform source sets.

### Feel

- Entrances and system responses move immediately and settle with deceleration.
- On-screen movement accelerates then decelerates.
- Determinate progress and continuous rotation may be linear.
- Gestures remain attached to input, preserve release velocity, and allow interruption.
- Recurring utility springs use low or no bounce; expressive bounce belongs to rare moments.
- Recurring duration-based motion usually stays below `300ms`; navigation, sheets, explanations, and spring settling may run longer when playback justifies it.
- Group stagger is rare, `30–80ms` between items, capped, and never blocks interaction.

Prefer themed specs:

```kotlin
MaterialTheme.motionScheme.fastSpatialSpec<Float>()
MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
MaterialTheme.motionScheme.slowSpatialSpec<Float>()
MaterialTheme.motionScheme.fastEffectsSpec<Float>()
MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
MaterialTheme.motionScheme.slowEffectsSpec<Float>()
```

When custom behavior requires exact curves:

```kotlin
val StrongEaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
val StrongEaseInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
val DrawerEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
```

## Craft checks

- Keep Material indication as the press-feedback baseline; add scale only when it improves a low-frequency interaction.
- Compose scale transitions default to zero; choose an explicit partial scale such as `0.95f` when scale is appropriate.
- Trigger-anchored custom surfaces use matching `TransformOrigin`; centered dialogs remain centered.
- Shared identity may justify `SharedTransitionLayout` when continuity benefit exceeds complexity.
- Use `graphicsLayer` for visual-only transforms, lambda `offset` for placement, and layout animation when siblings must follow.
- Gesture boundaries use resistance where appropriate; cancellation settles quickly; extra pointers do not cause jumps.
- Preserve focus, semantics, click targets, and final state at zero duration. Remove nonessential travel, parallax, bounce, and ambient loops under reduced motion.

## Verify

Inspect at normal speed and Android animator scale `10x`; verify `0x` reaches final visual and semantic state. Test touch on a physical Android device and mouse/keyboard on desktop when affected. Use Perfetto/System Trace only for frame-delivery claims.

Complete when the proposed or implemented interaction has one named purpose, fits its frequency, uses the smallest suitable Compose/Material mechanism, preserves accessibility and interruption, and records unverified device feel or profiling checks.
