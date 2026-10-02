# R8 Analysis Report

Omit empty optional sections. Quantitative fields appear only when present in the analyzer artifact.

## Configuration

- **AGP / R8:** `<detected versions>`
- **Finding:** `<path:line and configuration issue>`
- **Evidence:** `<observed value and effect on shrinking>`
- **Action and validation:** `<smallest change to evaluate and exact build/runtime check>`

## Global disable rules

### `<rule>`

- **Source:** `<path:line>`
- **Evidence:** `<scope and effect>`
- **Action and validation:** `<removal/refinement and release-build check>`

## Quantitative summary

<!-- Quantitative analyzer output only. -->

- **Optimization score:** `<artifact value>`
- **Shrinking score:** `<artifact value>`
- **Obfuscation score:** `<artifact value>`

## Keep-rule findings

### `<rule text>`

- **Source:** `<path:line>`
- **Evidence:** `<quantitative artifact values, or reflective/dependency/broad-match evidence>`
- **Assessment:** `redundant | overly broad | required | uncertain`
- **Confidence:** `high | medium | low`
- **Action and validation:** `<remove/refine/retain and the smallest release-build/runtime test>`

## Subsumed keep rules

### `<redundant rule>`

- **Source:** `<path:line>`
- **Subsumed by:** `<broader rule and source>`
- **Evidence:** `<artifact or exact rule comparison>`
- **Action and validation:** `<change and check>`

## Historical comparison

<!-- Include only when comparable prior artifact-backed scores exist. -->

| Metric | Previous | Current | Change |
| --- | ---: | ---: | ---: |
| Optimization | | | |
| Shrinking | | | |
| Obfuscation | | | |

## Limitations

- `<missing artifacts, unavailable dependency rules, or behavior requiring runtime validation>`
