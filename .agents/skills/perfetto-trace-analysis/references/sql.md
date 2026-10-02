# Perfetto SQL

Use the [Perfetto standard-library reference](perfetto-stdlib.md) to verify every non-prelude table/view schema, column, and required `INCLUDE PERFETTO MODULE` before execution. Prefer a standard-library abstraction over custom interval arithmetic.

## Query rules

- Join processes/threads with `upid`/`utid`; OS `pid`/`tid` values may be reused.
- Qualify columns with table aliases.
- Use `EXTRACT_ARG(arg_set_id, 'key')` for arguments.
- Use `=` for exact text and `GLOB` for wildcard matching; normalize case explicitly when needed.
- Treat `dur = -1` as trace-end truncation: `IIF(dur = -1, trace_end() - ts, dur)`.
- Match overlapping intervals with `start1 < end2 AND start2 < end1`; compute overlap as `MIN(end1, end2) - MAX(start1, start2)` only when no standard helper fits.
- For `SPAN_JOIN`, materialize input with `CREATE PERFETTO TABLE`, verify intervals do not overlap within each input partition, and use `PARTITIONED <key>` only to separate independent key groups. Partitioning does not repair overlapping intervals inside one group.
- Make created objects rerunnable: `CREATE OR REPLACE PERFETTO …`; drop SQLite virtual tables/indexes before recreating them.

Useful routes include `android_thread_slices_for_all_startups`, `counter_track` joined to `counter`, and `linux.cpu.frequency` / `cpu_frequency_counters`. Verify availability in the bundled standard-library reference.

## Workflow

1. Define the claim, target, interval, and required observations. Treat user SQL as a starting point whose intent should be preserved and whose defects should be explained.
2. Search the standard-library reference by domain, then read only matching module/schema sections.
3. Draft the smallest self-contained query. Validate syntax, modules, columns, identifiers, incomplete durations, interval overlap, and rerun safety.
4. Execute with an available `trace_processor` using the environment's supported invocation. If the tool is absent, report setup; download only when authorized.
5. Correct failures without weakening the analytical question. Stop when the query supplies the required evidence or the trace/schema cannot observe it.

Keep schema research internal. Include executed SQL in the response when the user requests it or when it is needed to substantiate a reported claim. Temporary SQL files belong in an authorized temporary/workspace location and are removed after use.

Complete when every reported value comes from an executed, schema-verified query and query limitations are explicit.
