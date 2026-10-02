# Matt Pocock skill adaptations

The missing `to-spec`, `to-tickets`, `implement`, and `implement-spec` folders were copied from [mattpocock/skills](https://github.com/mattpocock/skills/tree/d81f3a183412e71a5b1e84ca21bc1a35eea03a60/skills/engineering) at commit `d81f3a183412e71a5b1e84ca21bc1a35eea03a60` and then adapted locally.

The [shared contract](alignment-engineering/SKILL.md) joins human alignment to installed `pstack:poteto-mode` engineering. Local changes cover grilling, domain recording, prototypes, handoffs, spec synthesis, ticket creation, and implementation entry points. Existing unrelated skills retain their behavior.

The upstream [MIT license](MATT-POCOCK-LICENSE.txt) applies to the imported material.

Before an upstream refresh, review local changes against this pinned source. Preserve the shared contract and its entry-point links. `skills-lock.json` remains the existing installer record. It does not describe these local adaptations or newly copied skills.

Run `python .agents/skills/tests/test_alignment.py` from the repository root to check the local integration structure. This check does not establish live agent behavior.
