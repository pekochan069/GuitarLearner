## Rules

- Rendering composables in `:ui` receive `:presentation:contract` state and emit events. They must not access repositories, services, DI graphs, or platform effects.
- Circuit presenter composables in `:presentation:logic` may use injected domain capabilities and Compose runtime APIs.
- Before handing off architectural changes, run `verifyModuleBoundaries`, product lint, and the affected tests. Read `docs/agents/architecture-verification.md` for guard coverage and limits.

## Agent skills

### Issue tracker

Use GitHub Issues. Read `docs/agents/issue-tracker.md` before issue operations.

### Triage labels

Use the default triage labels. Read `docs/agents/triage-labels.md` before triaging.

### Domain docs

Use a single-context layout. Read `docs/agents/domain.md` before exploring the codebase.
