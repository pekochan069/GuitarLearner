# Material 3 Compose Navigation

Choose navigation from destination count and hierarchy, supported windows, content relationships, and input modes. Breakpoints describe available space; they do not mandate a component swap.

## Components

- **Navigation bar:** a few peer destinations when bottom placement supports the task and input mode.
- **Navigation rail:** peer destinations when side placement preserves content and pointer/keyboard or wider layouts benefit.
- **Drawer:** larger or grouped destination sets, secondary destinations, or navigation that should remain concealed/expandable.
- **Permanent drawer:** wide layouts where persistent hierarchy improves orientation and leaves adequate content width.
- **Tabs:** sibling views within one destination, not top-level app navigation.
- **Top app bar:** title, hierarchy/up action, and contextual actions; it complements rather than replaces destination navigation.

Use one clear primary navigation model per window state. Preserve destination identity and back-stack behavior when adapting presentation.

## Adaptive implementation

Inspect installed Material3 adaptive/navigation versions before choosing APIs. `NavigationSuiteScaffold` can adapt navigation presentation, but destination content still owns any unpropagated insets. List-detail/supporting-pane scaffolds fit related content when simultaneous context improves the task; they are not mandatory at a width threshold.

Navigation state belongs above presentation changes so resizing does not reset selection or duplicate back stacks. Keep platform back behavior, deep links, restoration, and predictive back correct for the active target.

## Accessibility and input

- Keep focus order stable when navigation changes form.
- Expose selected state and meaningful labels through semantics.
- Support keyboard/D-pad traversal on targets that accept them; width alone does not imply touch-only input.
- Preserve approximately 48dp touch targets and visible focus/selection.

## Verification

Test supported widths around actual presentation changes, destination selection/restoration, back/deep links, keyboard/pointer/touch paths, system insets, and increased font scale. Record checks as pass/fail/not-run.
