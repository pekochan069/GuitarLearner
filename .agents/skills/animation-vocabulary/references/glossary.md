# Compose Motion Glossary

### Entrances and exits

- **Fade in / fade out** — A composable appears or disappears through alpha.
- **Slide in / slide out** — A composable enters or exits by translating from or toward an edge.
- **Scale in / scale out** — A composable grows or shrinks around a `TransformOrigin`, usually with a fade. A partial scale such as `0.95f` preserves visual continuity better than zero.
- **Pop in** — A scale entrance with slight spring overshoot.
- **Reveal** — Content becomes visible through animated clipping, masking, or bounds.
- **Enter / exit transition** — Motion attached to adding or removing content, commonly through `AnimatedVisibility`.

### Sequencing and timing

- **Keyframes** — Values declared at specific timestamps; Compose interpolates between them with `keyframes`.
- **Interpolation / tween** — Generation of intermediate values between states over a known duration.
- **Stagger** — Related animations start with small delays, producing a cascade.
- **Orchestration** — Several animations coordinated as one state change.
- **Delay** — Time before motion begins.
- **Duration** — Time a duration-based animation takes.
- **Stepped animation** — Motion divided into discrete values rather than continuous interpolation.

### Movement and transforms

- **Translate** — Move content along X or Y.
- **Scale** — Make content larger or smaller.
- **Rotate** — Turn content around an axis.
- **3D tilt / flip** — Rotate around X or Y to suggest depth.
- **Transform origin** — Anchor used by scale and rotation; represented by Compose `TransformOrigin`.
- **Origin-aware animation** — Motion starts from its spatial source, such as a menu expanding from its trigger rather than its center.

### State and navigation transitions

- **Crossfade** — Old content fades out while new content fades in at the same location; Compose provides `Crossfade`.
- **Continuity transition** — Motion visually connects before and after so users retain orientation.
- **Morph** — One visual form changes continuously into another.
- **Shared element transition** — Content preserves identity while moving or resizing between screens, commonly through `SharedTransitionLayout`.
- **Shared bounds transition** — Different source and destination content share an animated container boundary.
- **Layout animation** — Intentional position, size, measurement, or placement motion where surrounding content follows; examples include `animateContentSize`, `animateItem`, and `animateBounds` inside a stable `LookaheadScope`.
- **Animated content** — State-driven replacement of one composable with another through `AnimatedContent`.
- **Direction-aware transition** — Forward and backward navigation move in opposite directions to preserve spatial meaning.
- **Predictive back** — Back-navigation progress follows the user's gesture before commit or cancellation.

### Feedback and gestures

- **Press / tap feedback** — Immediate visual response to a press. Material indication or ripple is normally the baseline; subtle scale is optional.
- **Hover effect** — Pointer-only feedback while a mouse or stylus hovers over content.
- **Hold to confirm** — Progress accumulates while a press is held before a consequential action commits.
- **Drag** — Directly moving content with a pointer or touch gesture.
- **Drag to reorder** — Moving an item while peers adjust to its prospective position.
- **Swipe to dismiss** — Dragging content away to remove or close it.
- **Rubber-banding** — Increasing resistance beyond a drag boundary, followed by return.
- **Ripple / indication** — Material press feedback emitted from an interaction source.
- **Shake / wiggle** — Brief lateral motion signaling rejection or error; use sparingly and never as the only signal.

### Easing and physics

- **Easing** — Rate at which interpolated motion accelerates or decelerates.
- **Ease-out / deceleration** — Starts fast and settles slowly; useful for entrances and system responses.
- **Ease-in / acceleration** — Starts slowly and finishes fast; useful only when departure semantics justify it.
- **Ease-in-out** — Accelerates then decelerates; useful for movement already on screen.
- **Linear** — Constant rate; appropriate for determinate progress or continuous rotation.
- **Cubic Bézier easing** — Curve described by four control values and represented by `CubicBezierEasing`.
- **Motion scheme** — Material 3's themed family of spatial and effects animation specifications, exposed through `MaterialTheme.motionScheme`.
- **Spring** — Physics-based motion configured by stiffness and damping rather than only duration.
- **Stiffness** — Strength pulling a spring toward its target; higher values feel faster and tighter.
- **Damping ratio** — Rate at which oscillation settles; lower values bounce more.
- **Bounce / overshoot** — Motion crosses its target before settling.
- **Velocity** — Speed and direction carried by gesture or spring motion.
- **Decay animation** — Motion that continues from initial velocity and slows through friction, commonly `animateDecay`.
- **Interruptible animation** — Motion that can retarget or reverse from its current value without restarting.
- **Perceptual duration** — Time until motion appears settled, especially for springs without a fixed endpoint time.

### Compose animation APIs

- **`animate*AsState`** — State-driven animation of one value.
- **`Transition` / `updateTransition`** — Coordinated animation of several values from one state machine.
- **`AnimatedVisibility`** — State-driven enter and exit transitions for conditional content.
- **`AnimatedContent`** — Transition between content selected by target state.
- **`Animatable`** — Coroutine-controlled value supporting snap, animate, stop, and velocity-aware motion.
- **`graphicsLayer`** — Draw-layer transforms and alpha without changing measured layout.
- **Lookahead** — Future-layout information used to animate toward target bounds.
- **`animateItem`** — Lazy-layout item appearance, disappearance, and placement motion.

### Looping and ambient motion

- **Loop** — Repeated animation, finite or infinite.
- **Alternate / yoyo** — Repetition that reverses direction each cycle.
- **Pulse** — Repeating alpha or scale change drawing attention.
- **Float** — Gentle repeating positional drift.
- **Idle animation** — Motion while content awaits interaction.
- **Skeleton / shimmer** — Loading placeholder with moving luminance.
- **Number ticker** — Digits count or roll toward a new value.

### Performance and accessibility

- **Frame rate** — Frames rendered each second; device refresh rate sets the target.
- **Jank** — Visible stutter caused by missed frame deadlines.
- **Dropped frame** — Frame not produced before its deadline.
- **Composition** — Compose phase deciding what UI exists.
- **Layout** — Measurement and placement phase.
- **Draw** — Rendering phase; visual-only transforms can often read animated values here.
- **Layer animation** — Translation, scale, rotation, or alpha applied through `graphicsLayer` without moving surrounding layout.
- **Motion duration scale** — Platform or test setting that scales animation time; zero duration represents reduced or disabled motion on supported targets.
- **Reduced motion** — Preserve state comprehension while removing or shortening nonessential movement according to platform preference.

### Principles

- **Purposeful animation** — Motion serves feedback, spatial continuity, state indication, explanation, or relief from a jarring change.
- **Frequency of use** — Frequently repeated actions need shorter, subtler, or no motion.
- **Spatial consistency** — Motion preserves where content came from and where it went.
- **Perceived performance** — Responsive motion can make unchanged latency feel shorter.
- **Latest-phase read** — Read animated state in the latest phase that can produce the required effect: draw, then layout, then composition.
