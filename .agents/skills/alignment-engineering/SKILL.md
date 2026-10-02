---
name: alignment-engineering
description: Preserve human intent when using pstack poteto-mode for engineering. Use before implementation from a confirmed discussion, spec, or Wayfinder map, and when engineering findings change expected behavior or scope.
---

# Align intent, then engineer

Read this contract when combining Matt Pocock's alignment skills with `pstack:poteto-mode`.

## Route the work

- Use [grill-with-docs](../grill-with-docs/SKILL.md) for bounded human alignment. Use [wayfinder](../wayfinder/SKILL.md) when decisions span sessions.
- Use [grilling](../grilling/SKILL.md) for product choices, scope, constraints, and acceptance behavior. The human supplies these decisions.
- Use `pstack:poteto-mode` for engineering facts, design exploration, implementation, review, and verification. Load the installed skill by its qualified name. Keep its playbooks and principles in the installed package.
- Keep explicit invocation for planning commands. When the user authorizes a transition, follow the next skill without another confirmation of the same scope.

Use native Codex tools in place of `Skill`, `AskUserQuestion`, and `Agent`. Read linked instructions when a skill calls for them. Ask genuine preference questions through the available question tool or a final message. Research observable facts yourself. Before dispatch, read the project's `AGENTS.md` model configuration and pstack's provider-dispatch reference. This project's configured lanes use native `spawn_agent`, `gpt-6.1-sol`, and `max` effort.

## Confirm the contract

Before leaving alignment, present the destination, scope, exclusions, constraints, and observable acceptance examples. Include relevant failure cases and unresolved questions. Separate human decisions from measured facts and agent recommendations. Cite the issue resolutions, glossary terms, ADRs, and prototypes that establish them.

Ask the human to confirm this concrete contract once. An earlier explicit confirmation of the same contract counts. Silence, a `ready-for-agent` label, an empty question frontier, and a completed research ticket do not count. Confirmation of intent and authorization to implement are separate. If the user only requested planning, finish with the confirmed contract and its pointers.

For a direct engineering request with explicit behavior and scope, the user's instruction supplies intent and implementation authorization. Start work without manufacturing an interview. Use grilling when product intent remains unresolved, and honor a confirmation gate in an active grilling session.

Record confirmation beside the authoritative decision, with a context pointer to the user's response. In this repo, specs and Wayfinder decisions live in GitHub Issues. Consult `docs/agents/issue-tracker.md` before tracker operations. Keep the map as an index. Use `docs/agents/domain.md` for glossary and ADR placement. Record only settled domain terms and qualifying architectural trade-offs there. Link an ADR and its decision ticket rather than copying the full decision into both. Keep unconfirmed proposals in the discussion.

## Hand off to engineering

When implementation is authorized, give poteto-mode these context pointers in the conversation or [handoff](../handoff/SKILL.md), rather than creating another spec file:

- The confirmed contract and the user's implementation authorization.
- The source issue and resolution comments, plus relevant glossary terms and ADRs.
- The acceptance examples and the evidence needed to verify each one.
- Constraints, exclusions, blockers, and remaining empirical investigations.

Read the source bodies and comments before choosing a poteto playbook. Use Feature, Bug fix, or Refactoring for bounded work. Let poteto-mode route larger work to its multi-phase, figure-it-out, or orchestration workflows. Use [to-spec](../to-spec/SKILL.md) or [to-tickets](../to-tickets/SKILL.md) only when synthesis or a task graph earns a separate artifact. A small confirmed task can go straight to engineering.

Engineering owns reversible implementation choices within the confirmed contract. Execute, delegate, and verify without reopening those choices for permission. Preserve the user's explicit gates and the active platform's rules for external or irreversible actions.

## Return to alignment when intent changes

If evidence requires a change to user-visible behavior, scope, acceptance criteria, constraints, or a confirmed decision, identify the affected decision and show the evidence. Resume grilling for that branch. On a Wayfinder effort, add or reopen a decision ticket and block affected implementation work until the human resolves it. Continue independent work that remains within the contract. Pass this contract and the confirmed source pointers to every engineering delegate. Delegates return product forks to the parent rather than choosing for the human.

An empirical prototype may settle feasibility without a human reply. A prototype used to choose desired behavior needs the human's verdict. Keep prototypes throwaway until implementation is authorized. Engineering verification checks the acceptance examples and reports remaining gaps. Changed behavior goes through alignment before it becomes the new expected result.
