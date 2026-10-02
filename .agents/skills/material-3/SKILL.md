---
name: material-3
description: >
  Implement or audit the Material Design 3 design system with Jetpack Compose
  Material3: MaterialTheme, color/typography/shape tokens, dynamic color,
  components, adaptive component selection, accessibility, or Material 3
  Expressive behavior. Use edge-to-edge for system-inset defects.
user-invokable: true
argument-hint: "[component|theme|layout|scaffold|audit] [description or path]"
---

# Material Design 3

For explanation, review, or audit requests, inspect and report. For build or fix requests, make requested in-scope local changes and run relevant non-destructive validation. Confirm before external writes, dependency installation, destructive actions, or material scope expansion.

Material 3 is token-first, adaptive, and platform-versioned. Inspect installed Compose/Material versions before proposing experimental or expressive APIs. Platform support differs; do not infer parity from the design specification.

## Route

| Goal | Reference |
| --- | --- |
| color roles, tonal palettes, contrast | [color system](references/color-system.md) |
| typography, shape, elevation, motion basics | [typography and shape](references/typography-and-shape.md) |
| component choice and Compose mappings | [component catalog](references/component-catalog.md) |
| Compose app navigation and adaptive presentation | [Compose navigation](references/navigation-compose.md) |
| Compose adaptive layout, scaffolds, insets, foldables | [Compose layout](references/layout-compose.md) |
| light/dark/dynamic themes | [theming and dynamic color](references/theming-and-dynamic-color.md) |

Load only references needed by the request.

## Compose baseline

- Use `androidx.compose.material3` components and `MaterialTheme` roles before custom primitives.
- Use semantic color pairs such as `primary`/`onPrimary` and surface/container roles; intentional brand or data-visualization colors remain valid when contrast and theming behavior are explicit.
- Use the Material type scale and shapes through theme roles; one-off values need a concrete visual reason.
- Prefer adaptive scaffolds/window classes for form factors the product supports. Constrain readable content on large widths rather than stretching it indefinitely.
- Handle system bars and IME through one coherent inset owner; avoid duplicated padding.
- Preserve semantics, focus, approximately `48.dp` touch targets, and tested contrast.
- Reuse Material component motion and `MaterialTheme.motionScheme`; verify availability against the installed version.

A `4.dp` base grid supports fine alignment; `8.dp` is the common layout rhythm. Follow project tokens when they already encode this system.

## Implementation workflow

1. Resolve target component/screen, active source set, supported window classes, installed Material version, and requested outcome.
2. Load the matching reference and inspect existing theme/components.
3. Reuse current tokens and Material components; add the smallest custom behavior needed by the design.
4. Validate the affected compile target plus relevant light/dark, size-class, input, and accessibility states.
5. Report changed paths, validation, caveats, and unverified device behavior.

Implementation is complete when requested states use coherent theme roles, supported layouts remain usable, semantics and contrast are preserved, and validation is recorded as pass/fail/not-run.

## Audit

When asked for an audit, inspect only applicable categories and cite `file:line` evidence:

| Category | Check |
| --- | --- |
| Color | semantic roles, intended pairs, contrast, light/dark behavior |
| Typography | Material roles and readable hierarchy |
| Shape | theme roles versus unexplained magic values |
| Elevation | tonal and shadow behavior appropriate to component/background |
| Components | suitable Material3 component and installed API availability |
| Layout | supported size classes, readable width, insets, hinge constraints when applicable |
| Navigation | component and structure fit each supported window class |
| Motion | Material-owned motion/spec reuse and reduced-motion behavior |
| Accessibility | semantics, focus, target size, contrast |
| Theming | coherent theme ownership and dynamic-color policy |

Score applicable categories `0–10`; use `N/A` for unsupported categories and exclude them from the denominator. `0` means applicable behavior is absent or failing. Each score needs evidence. Prioritize fixes by user impact, not token purity.

```markdown
# MD3 Audit

Target: <path or screen>
Overall: <earned points / applicable points>

| Category | Score | Evidence |
| --- | ---: | --- |

## Critical issues
- `file:line` — issue, impact, exact fix

## Warnings
- `file:line` — issue and fix

## Passing
- evidence-backed strengths

## Validation and limitations
- commands/checks: pass | fail | not run
- unverified behavior
```

Audit is complete when every applicable category has evidence or `N/A`, critical issues have exact fixes, and performed versus recommended checks are distinct.
