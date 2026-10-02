# Issue tracker

Issues and specs live in GitHub Issues for `pekochan069/GuitarLearner`.
Use the `gh` CLI from this repository.

## Operations

- Create: `gh issue create --title "..." --body-file <path>`
- Read: `gh issue view <number> --comments`
- List: `gh issue list --state open --json number,title,body,labels,comments`
- Comment: `gh issue comment <number> --body-file <path>`
- Label: `gh issue edit <number> --add-label "..." --remove-label "..."`
- Close: `gh issue close <number> --comment "..."`

For multiline text, write the exact body to a temporary UTF-8 file and use
`--body-file`.

When a skill says to publish to the issue tracker, create a GitHub issue.
When it says to fetch a ticket, read the issue and its comments.

## Pull requests as a triage surface

PRs as a request surface: no.

## Wayfinding

- Keep the map in one issue labelled `wayfinder:map`.
- Link child tickets as GitHub sub-issues. If unavailable, use a task list
  in the map and add `Part of #<map>` to each child.
- Label children `wayfinder:<type>`, where type is `research`, `prototype`,
  `grilling`, or `task`.
- Record blockers using GitHub issue dependencies. Use the blocker's
  database ID, obtained with `gh api repos/pekochan069/GuitarLearner/issues/<n> --jq .id`.
  Add it with `gh api --method POST repos/pekochan069/GuitarLearner/issues/<child>/dependencies/blocked_by -F issue_id=<id>`.
- If dependencies are unavailable, add `Blocked by: #<n>` to the child.
- Select the first open, unassigned child in map order whose blockers
  are all closed.
- Claim with `gh issue edit <n> --add-assignee @me`.
- Resolve by commenting, closing the child, and adding the result link
  to the map's Decisions-so-far.
