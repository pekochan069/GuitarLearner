# Compose Multiplatform Motion Audit

Use project dependency versions and source-set boundaries as constraints. Prefer Material component defaults and `MaterialTheme.motionScheme` before custom specifications.

## 1. Purpose and frequency

Every animation names one purpose: feedback, spatial continuity, state indication, explanation, or prevention of a jarring change.

| Frequency | Decision |
| --- | --- |
| 100+ times/day, keyboard navigation, core practice loop | Reject decorative transitions and added latency; preserve immediate functional visualization, direct manipulation, state feedback, and platform indication |
| Tens/day | Short and subtle |
| Occasional | Standard motion |
| Rare / first-time | Delight allowed |

Flag motion that delays input, decorates readable data, or repeats heavily without purpose.

## 2. Specifications and duration

Preference order:

1. Material component-owned motion.
2. `MaterialTheme.motionScheme` spatial/effects specs.
3. Existing project spec.
4. New `spring`, `tween`, or `CubicBezierEasing` only when behavior needs it.

Useful themed specs:

```kotlin
MaterialTheme.motionScheme.fastSpatialSpec<Float>()
MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
MaterialTheme.motionScheme.slowSpatialSpec<Float>()
MaterialTheme.motionScheme.fastEffectsSpec<Float>()
MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
MaterialTheme.motionScheme.slowEffectsSpec<Float>()
```

Explicit custom curves when project requirements justify them:

```kotlin
CubicBezierEasing(0.23f, 1f, 0.32f, 1f) // strong deceleration
CubicBezierEasing(0.77f, 0f, 0.175f, 1f) // accelerate/decelerate
CubicBezierEasing(0.32f, 0.72f, 0f, 1f) // drawer-like settle
```

Recurring duration-based UI motion normally stays below `300ms`; sheets, navigation, explanations, and springs may settle longer. Judge springs by observed settling, not a nominal duration. Flag repeated arbitrary `tween` values, not every custom duration.

Acceleration can fit an exit when it preserves responsiveness. Do not apply a universal “never ease-in” rule to Compose or Material defaults.

## 3. Spatial continuity

- Compose `scaleIn()` and `scaleOut()` default to zero scale. When scale is chosen, use an explicit partial scale such as `0.95f` unless zero has a deliberate visual meaning.
- Trigger-anchored custom surfaces use a matching `TransformOrigin`; centered dialogs stay centered.
- Forward/back transitions should communicate hierarchy.
- Shared identity may justify `SharedTransitionLayout`, `sharedElement`, or `sharedBounds`.
- Expansion should move related content coherently. Prefer `animateContentSize`; use `animateBounds` only inside a stable `LookaheadScope` with named owner and coordinate space.
- Android predictive back belongs in `androidMain` unless current shared APIs support it.

Flag teleporting state, wrong origin, broken shared identity, or arbitrary directional motion.

## 4. Interruptibility and gestures

- `animate*AsState` and `Transition` suit state that can retarget.
- `Animatable` suits imperative or gesture-driven motion; create it with `remember`.
- `snapTo` keeps direct manipulation attached to the pointer.
- `animateTo(initialVelocity = …)` and `animateDecay` preserve release velocity.
- Material drag, swipe, and sheet components beat custom gesture machinery.
- `keyframes` fit fixed choreography, not reversible gesture state.
- Deliberate holds may be slow; cancellation and system response should snap.

Flag lost velocity, fixed distance-only dismissal, hard boundaries without resistance, restart-from-origin behavior, or extra pointers causing jumps. Use density-aware velocity/position thresholds or component defaults.

## 5. Compose phases and performance

Read animated state in the latest phase that can produce the intended effect:

- visual-only translation, scale, rotation, alpha: `graphicsLayer { … }`;
- placement that must affect position but not composition: lambda `offset { IntOffset(...) }`;
- surrounding layout must follow: `animateContentSize`, `animateItem`, or another layout animation; use `animateBounds` only inside a stable `LookaheadScope`, keep scope identity stable, and account for its coordinate space;
- composition: only when animated state changes composable structure or cannot be deferred.

Inspect per-frame allocations, unstable state, snapshot writes, recomposition, measurement, drawing cost, and large layer usage. Layout animation is allowed when layout continuity is the purpose. `graphicsLayer` is not automatically faster in every case.

Performance severity requires evidence: Compose compiler/layout tooling, frame timing, Perfetto/System Trace, Macrobenchmark, or reproducible missed deadlines.

## 6. Accessibility and semantics

- Verify Android animator duration scale `0x`. Desktop may need explicit product policy. Verify iOS Reduce Motion only when Gradle declares an iOS target.
- Zero-duration paths must reach final visual and semantic state.
- Motion cannot be the only status or error signal.
- Focus order, focus restoration, click targets, content descriptions, and announcements must survive transitions.
- Remove nonessential travel, parallax, bounce, flashing, and ambient loops under reduced motion; retain useful alpha/color/state feedback.
- Infinite motion requires purpose, lifecycle control, and an accessible reduced-motion path.

Flag hidden focus, delayed semantics, focus moving with disappearing content, or state that remains inaccessible until animation completes.

## 7. Cohesion and source sets

- Motion personality stays consistent: learning flow remains crisp; rare milestones may be expressive.
- Repeated custom behavior belongs in shared specs only after repetition exists.
- Avoid parallel near-identical durations or springs when `MaterialTheme.motionScheme` fits.
- Common behavior uses APIs available in `commonMain` for installed Compose version.
- Android-only imports stay out of `commonMain`; platform-specific behavior lives in matching source sets.
- Do not replace correct Material motion merely to impose custom taste.

Stagger rare group entrances by `30–80ms`, cap delayed items, and keep interaction enabled.


## Reject findings when

- Material already owns suitable motion or indication.
- Layout motion is necessary for surrounding placement.
- An existing motion scheme or project spec supplies the behavior.
- The API is unavailable in the active source set or installed version.
- Duration scale already handles timing and no semantic issue remains.
- Evidence does not support the performance claim.
- Repository documentation records the behavior as deliberate.

## Severity

- **HIGH** — blocked input, broken focus/semantics, harmful infinite motion, platform API in the wrong active source set, or severe measured jank.
- **MEDIUM** — incorrect phase, non-interruptible reversible gesture, accidental zero-scale entrance, missing reduced-motion validation, or broken spatial continuity.
- **LOW** — duplicated arbitrary specs or optional cohesion polish.
