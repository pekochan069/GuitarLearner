---
name: perfetto-trace-analysis
description: Analyze a supplied Perfetto trace to determine the cause of Android latency, jank, memory, I/O, IPC, CPU, graphics, or power behavior with query-backed evidence.
license: Complete terms in LICENSE.txt
metadata:
  author: Google LLC
  last-updated: '2026-05-14'
  keywords: [Perfetto, trace analysis, Android performance, debugging, profiling, jank, SQL]
---

# Perfetto Trace Analysis

Trace files are read-only evidence. For analysis requests, inspect and report. Create a scratchpad only in an authorized writable workspace location; never install tools or edit repository configuration without authorization.

## Resources

- SQL generation and schema validation: [Perfetto SQL](references/sql.md). Read before writing SQL; use documented schemas.
- Domain hints: [CPU](references/hints_cpu.md), [Graphics](references/hints_graphics.md), [I/O](references/hints_io.md), [IPC](references/hints_ipc.md), [Memory](references/hints_memory.md), [Power](references/hints_power.md). Read only the domains relevant to the symptom or observed evidence.
- Perfetto standard library: [stdlib](references/perfetto-stdlib.md).

## Workflow

1. Resolve the package, symptom, and time range from the request and trace. Ask only when multiple plausible targets would materially change the result.
2. Start with the relevant metric or a broad query, then narrow using verified schemas from the SQL reference.
3. Record each executed metric/query, target, timestamp range, observed values, relevant IDs, and result. Keep unsupported hypotheses out of evidence notes.
4. For suspicious wall time, query `thread_state` over the exact overlapping interval. Distinguish running, runnable, sleeping, and uninterruptible sleep. When blocked, identify the blocker or label it unresolved.
5. Run one system-wide sanity check for a materially larger competing stall.
6. Stop when the requested cause is supported, disproved, or not observable in the trace. Report trace limitations instead of searching indefinitely.

Use overlap predicates for time windows so slices crossing a boundary are retained. Start broad enough to avoid name assumptions, then narrow with evidence. Keep schema research and discarded hypotheses out of the user-facing response.

## Response contract

Lead with the root cause or current determination.

- **Evidence:** claim, process/thread, timestamp or duration, executed metric/query, and observed value.
- **Caveats:** missing trace data and unresolved dependencies; label claims confirmed, likely, disproved, or unobservable.
- **Next action:** smallest useful remediation or follow-up capture.
- **Scratchpad:** path only when one was created.

Analysis is complete when the requested behavior has a supported determination, one competing system-wide cause was checked, every reported claim traces to an executed query/metric, and unobservable behavior is explicit.
