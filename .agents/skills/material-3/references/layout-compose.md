# Material 3 Compose Layout

Choose layout from supported windows, content relationships, and input modes—not a fixed phone/tablet threshold.

## Recon

Derive active targets and installed Material3/adaptive versions from Gradle. Record supported window sizes, posture/hinge requirements, navigation depth, content density, pointer/keyboard needs, and existing inset owner.

## Adaptive choices

- Compact content often uses one pane, but available width and task focus decide.
- List-detail and supporting-pane scaffolds fit persistent relationships when simultaneous context improves the task.
- Navigation bar, rail, drawer, and permanent navigation are options selected from width, destinations, content, and input—not automatic breakpoint swaps.
- Modal versus side sheets depends on available width, task interruption, and content; width alone does not require conversion.
- Foldable hinge avoidance applies only to supported devices/postures and should use window-layout information rather than hard-coded gaps.

Prefer installed Material3 adaptive scaffolds/window-size APIs. Verify experimental opt-ins against dependency versions.

## Spacing and width

Use a 4dp base grid with 8dp as the common layout rhythm, expressed through project tokens. Preserve smaller increments when component geometry needs them. Constrain long-form content to a readable maximum width on large windows; do not constrain surfaces or grids without a content reason.

## Insets

Assign one owner for system-bar and IME insets. Material/scaffold components may already handle their own bars. Pass scaffold padding to scrollables through `contentPadding` when content should draw behind bars. Keep decorative surfaces edge-to-edge while interactive/readable content remains safe.

## Input and accessibility

Window width does not determine input mode. Any supported target may have touch, keyboard, mouse, stylus, or accessibility navigation. Preserve focus order/visibility, approximately 48dp touch targets, pointer feedback where relevant, semantics, and readable text at increased font scale.

## Verification

Test the product's supported compact/medium/expanded widths plus boundary widths where structure changes. Include light/dark, font scale, keyboard/pointer paths, system bars/IME, and fold posture only when supported. Record checks as pass/fail/not-run.
