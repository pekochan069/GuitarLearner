---
name: implement-spec
description: Execute a confirmed spec and its ticket graph through poteto-mode.
disable-model-invocation: true
---

Read the [alignment and engineering contract](../alignment-engineering/SKILL.md). Read the spec, its resolution comments, and implementation tickets with their blockers. Keep Wayfinder decision tickets distinct from implementation tickets. Verify the confirmed contract and the user's implementation authorization before starting affected work.

Load the installed `pstack:poteto-mode` skill. Let it select the matching workflow for the graph's size and delivery requirements. Pass source pointers and acceptance examples rather than reproducing its orchestration steps here. A small graph can stay with one engineer. Delegates receive the shared contract and source pointers. Follow the project's `AGENTS.md` model configuration for every configured role.

Work only tickets whose prerequisite work and human decisions are resolved. Return new product forks to the parent for [grilling](../grilling/SKILL.md) or a [Wayfinder](../wayfinder/SKILL.md) decision. Continue unaffected tickets. Resolve implementation tickets only after their acceptance checks pass, using the configured tracker workflow. Report remaining blockers and verification gaps separately from completed work.