---
name: improve-animations
description: Audit existing motion across a Compose codebase and, only when explicitly requested, write implementation plan documents. Use for repository-wide motion audits or roadmaps, not missing-motion discovery or bounded changed-code review.
---

# Improving Compose Animations

Application source is read-only. Audit requests report in chat; only explicit `plan` or `reconcile` requests may write under `plans/` or an existing motion-plan directory. Run no installs, commits, formatters, or mutating build tasks.

Read [AUDIT.md](AUDIT.md) before auditing and [PLAN-TEMPLATE.md](PLAN-TEMPLATE.md) only when planning.

## 1. Recon

Record dependency versions, active targets and source sets from Gradle, existing animation/gesture APIs, Material-owned motion, `MaterialTheme.motionScheme`, repeated project specs, interaction frequency, accessibility paths, and available validation tasks.

Recon is complete when installed API constraints, platform boundaries, and existing motion ownership are known for every in-scope area.

## 2. Audit

Apply every in-scope category in [AUDIT.md](AUDIT.md). For larger repositories, read-only subagents may divide by category or app area; each returns only `file:line` evidence and cannot delegate or mutate.

| Effort | Coverage | Typical output |
| --- | --- | --- |
| `quick` | High-frequency UI | About five high-impact findings |
| `standard` | All interactive UI | Full prioritized table |
| `deep` | Whole repository and active targets | Full table plus low-priority cohesion issues |

For missing-motion discovery, route to `find-animation-opportunities`.

## 3. Vet and report

Re-read every cited location and apply the rejection and severity rules in `AUDIT.md`. Report findings by leverage:

| # | Severity | Category | Location | Evidence | Fix summary |
| --- | --- | --- | --- | --- | --- |

For an audit request, report vetted findings and stop. Write plan files only when the invocation explicitly requests `plan` or a roadmap. If planning was requested without a selection, choose the top `3–5` findings by leverage.

Audit is complete when every in-scope category was checked, every reported location was re-read, each finding has direct evidence and severity, and commands/manual checks are recorded as passed, failed, or not run.

## 4. Plan

Write one selected finding per `NNN-short-slug.md`, merging only findings with identical files and fix pattern. Follow [PLAN-TEMPLATE.md](PLAN-TEMPLATE.md), stamp the current commit, and update the plan index. Plans name source-set ownership, installed API constraints, exact Kotlin target, reduced-motion behavior, validation, and residual risk.

## Invocation variants

- bare: recon, audit, vet, and report; keep files unchanged
- `quick` / `deep`: change audit coverage; keep files unchanged
- category name: audit only that category; keep files unchanged
- `plan <description>`: write the requested plan
- `plan`: write plans for the top `3–5` vetted findings
- `reconcile`: update existing motion plans only

When code cannot establish feel or frame delivery, require device playback or profiling rather than guessing.
