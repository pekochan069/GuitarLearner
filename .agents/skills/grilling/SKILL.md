---
name: grilling
description: Grill the user relentlessly about a plan, decision, or idea. Use when the user wants to stress-test their thinking, or uses any 'grill' trigger phrases.
---

Interview the user relentlessly until you reach a shared understanding. Map this as a **design tree**: every decision branches into the decisions that hang off it.

When paired with poteto-mode, read the [alignment and engineering contract](../alignment-engineering/SKILL.md). Work only the branches needed for the stated destination. Keep unrelated possibilities out of scope.

Work the tree in **rounds**. The **frontier** is every decision whose prerequisites are already settled: the questions you can ask _now_ without guessing at answers you haven't heard yet. Ask the whole frontier in one round: number each question and give your recommended answer. Then wait for the user's answers before the next round.

Format a round like so:

```
❓ **Q1** - **<question title>**: <question body, might be multiple paragraphs, including multiple choices>

➡️ <your recommended answer>

---

❓ **Q2** - **<question title>**: <question body, might be multiple paragraphs, including multiple choices>

➡️ <your recommended answer>
```

Each round the user answers reshapes the tree: settled decisions push the frontier outward and unblock questions that depended on them. Recompute the frontier and ask the next round. A question whose answer depends on another question still open in this round belongs to a _later_ round, not this one.

Finding facts is your job. When a frontier question needs an environmental fact, dispatch a subagent to find it. A running exploration is an unsettled prerequisite, so only downstream questions wait for its report. Ask the rest of the frontier now. Product decisions belong to the user. When paired with poteto-mode, engineering choices within confirmed intent belong to its engineering workflow under the shared contract.

The session is done when the in-scope frontier is empty and the user confirms the concrete contract. Show the accepted behavior, constraints, exclusions, and acceptance examples for confirmation. Research and throwaway experiments can proceed during alignment. Production implementation starts when the contract is confirmed and implementation is authorized. Existing confirmation and authorization count for unchanged scope.
