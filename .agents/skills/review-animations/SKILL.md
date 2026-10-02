---
name: review-animations
description: Review a bounded Compose animation diff or named changed files. Use for changed motion code only, not repository-wide audits, planning, or discovery of missing motion.
---

# Reviewing Compose Animations

Review motion changes only. Keep source unchanged unless the user separately requests fixes.

## Workflow

1. Read the bounded diff or named changed files and relevant callers.
2. Apply every applicable rule in [STANDARDS.md](STANDARDS.md).
3. Cite direct `file:line` evidence. Treat feel and performance as unverified unless playback, device testing, or profiling supplies evidence.
4. Report actionable findings, validation evidence, and verdict.

## Findings

Use one row per issue:

| Before | After | Why |
| --- | --- | --- |
| `file:line — current behavior` | `exact target` | `purpose/frequency/API/accessibility/performance evidence` |

Group commentary by impact, omitting empty groups:

1. blocking behavior and accessibility;
2. motion to delete or reduce;
3. interruptibility and gesture physics;
4. phase and measured performance;
5. spatial continuity and cohesion;
6. source-set and specification consistency.

## Evidence

- files, callers, and source sets inspected;
- dependency and active-target evidence used;
- commands run with pass/fail/not-run;
- feel, reduced-motion, accessibility, and profiling checks performed or explicitly unverified;
- residual risks.

## Verdict

- **Block** — a HIGH finding remains.
- **Approve** — no blocking finding remains and available compile/test evidence passes. Mark device feel, reduced-motion, or profiling checks unverified when they were not performed; never imply they passed.

Complete when every applicable standard was checked, every finding has direct evidence and an exact correction, and the verdict matches recorded validation.
