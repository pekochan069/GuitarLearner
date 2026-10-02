---
name: implement
description: Implement confirmed work from a spec or tickets through poteto-mode.
disable-model-invocation: true
---

Read the [alignment and engineering contract](../alignment-engineering/SKILL.md). Fetch the source spec or ticket and its resolution comments. Verify that intent is confirmed and implementation is authorized. Reuse existing confirmation for unchanged scope. A tracker label alone is insufficient.

Load the installed `pstack:poteto-mode` skill. Give it the confirmed contract, source pointers, constraints, exclusions, and acceptance examples. Follow its matching engineering playbook through implementation, review, and verification. Its playbook owns tests, delegation, branches, commits, and PRs under the user's and repository's instructions.

If engineering evidence changes intent, return the affected branch to [grilling](../grilling/SKILL.md). Continue independent work within the confirmed contract. Finish by reporting which acceptance examples passed, the evidence, and any remaining gaps.