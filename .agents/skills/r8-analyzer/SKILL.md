---
name: r8-analyzer
description: Audit Android R8/ProGuard configuration and keep rules for global disables, redundancy, broad matches, reflection requirements, consumer-rule overlap, and measurable shrink impact. Read-only.
license: Complete terms in LICENSE.txt
metadata:
  author: Google LLC
  last-updated: '2026-07-18'
  keywords: [R8, proguard, keep rules, app size, optimization]
---

# R8 Analyzer

Inspect and report only; keep project files unchanged. Every finding needs a rule/config location and quantitative or heuristic evidence.

## 1. Configuration

Inspect module/root Gradle files, version catalogs, `gradle.properties`, R8/ProGuard files, dependency consumer rules when available, and generated mapping/usage/configuration artifacts. Apply [configuration checks](references/CONFIGURATION.md).

Report the detected AGP/R8 version and shrinking defects. Recommend an AGP upgrade only when required for a cited capability or analyzer path, with its compatibility caveat. Flag global disables such as `-dontshrink`, `-dontoptimize`, `-dontobfuscate`, and obsolete `android.enableR8.fullMode=false`.

## 2. Choose evidence path

### Quantitative

Use only when the installed R8 supports configuration analysis and a working project analyzer command is already available. If setup or output shape is uncertain, use heuristic mode rather than inventing metrics. Quantitative completion requires:

- analyzer command exits zero;
- `tmp/r8analysis/analysis_result.txt` exists;
- every reported score, impact, count, example class, or subsumption appears in that artifact.

Preserve the result until the response is complete.

### Heuristic

Otherwise inspect rules manually:

- compare dependency-owned rules with [known redundant rules](references/REDUNDANT-RULES.md);
- rank broad rules with [impact hierarchy](references/KEEP-RULES-IMPACT-HIERARCHY.md);
- verify reflective/native/serialization use with [reflection guidance](references/REFLECTION-GUIDE.md).

Heuristic mode never estimates scores, percentages, or affected-item counts. Each finding records rule location, matched risk pattern, code/dependency evidence, confidence, and a validation action.

## 3. Response

Return only the applicable Markdown sections from [report format](references/REPORT_FORMAT.md). Omit empty optional sections. Recommendations appear only in each finding's **Action and validation** field. Do not mention internal skill paths or analysis-path labels.

Analysis is complete when every in-scope configuration file was inspected, each finding has direct evidence, quantitative values are artifact-backed or omitted, and uncertainties are explicit.
