---
name: create-issue
description: Create a GitHub issue with Conventional Commits title, labels, milestone, duplicate check, and Voyager Roadmap project assignment.
---

# Create Issue

Create a new GitHub issue for the OneLiteFeatherNET/Voyager repository with proper labels, milestone, duplicate detection, and automatic assignment to the Voyager Roadmap project.

## Rules

- Issue title MUST follow Conventional Commits: `type(scope): short description` (e.g. `feat(server): add boost ring effect`).
- Issue body MUST be written in **English** — never German.
- Always check for duplicate issues before creating.
- Always add the issue to the Voyager Roadmap project (ID 19) after creation.
- A priority label (P0, P1, or P2) MUST always be assigned.
- Labels are always fetched live from the repo before applying.

## Steps

1. Ask the user for the following information via `AskUserQuestion`. Collect all inputs before proceeding:

   - **Title** — must follow Conventional Commits: `type(scope): short description`
   - **Body / description** — what the problem or feature request is, and what the acceptance criteria are
   - **Commit type** — one of `feat`, `fix`, `docs`, `chore`, `ci`, `refactor`, `test`
   - **Domain label(s)** — any of: `gameplay`, `database`, `infrastructure`, `setup` (optional, pick all that apply)
   - **Priority** — one of `P0` (critical), `P1` (high), `P2` (normal)
   - **Milestone** — pick from the list below (by number):
     - 1 — Sprint 1: Playable Loop
     - 2 — Sprint 2: Complete Cup Flow
     - 3 — Alpha v0.1
     - 4 — Beta v0.2
     - 5 — Release v1.0

2. Validate the title format. It must match the pattern `type(scope): short description`. Valid types are: `feat`, `fix`, `docs`, `chore`, `ci`, `refactor`, `test`. If the title does not match, tell the user the correct format and ask them to correct it before continuing.

3. Check for duplicate issues. Extract two or three keywords from the issue title and search open issues:
```bash
gh issue list --repo OneLiteFeatherNET/Voyager --state open --search "<keywords from title>"
```
If results are returned, list them (number, title, URL) and ask the user:
- "These open issues may be related. Do you want to proceed with creating a new issue, or would you like to use one of the existing ones instead?"
- Only continue if the user confirms creation.

4. Fetch available labels from the repo:
```bash
gh label list --repo OneLiteFeatherNET/Voyager
```

5. Map the commit type to a primary label:
   - `feat` → `enhancement`
   - `fix` → `bug`
   - `docs` → `documentation`
   - `chore` → `chore`
   - `ci` → `ci`
   - `refactor` → `cleanup`
   - `test` → `testing`

   Combine with domain labels chosen in step 1, the priority label (P0/P1/P2), and any other applicable labels from the fetched list (e.g. `sprint-1`, `sprint-2`, `blocked`, `breaking`).

6. Compose the issue body using the following template (fill in all sections in English):

```
## Problem / Feature

<!-- Describe the problem or feature request -->

## Acceptance Criteria

- [ ] ...

## Additional Context

<!-- Screenshots, logs, related issues, etc. -->
```

Replace the placeholder comments with the content the user provided in step 1. Keep acceptance criteria as checkboxes (`- [ ] ...`).

7. Create the issue:
```bash
gh issue create \
  --repo OneLiteFeatherNET/Voyager \
  --title "<type(scope): short description>" \
  --body "<full body from step 6>" \
  --label "<label1>,<label2>,..." \
  --milestone "<milestone title>"
```

Capture the issue URL returned by the command.

8. Add the issue to the Voyager Roadmap project (project ID 19):
```bash
gh project item-add 19 --owner OneLiteFeatherNET --url "<issue-url>"
```

9. Display the final issue URL and a summary:
   - Issue URL
   - Labels applied
   - Milestone assigned
   - Confirmation that the issue was added to the Voyager Roadmap project
   - Confirmation that no duplicate was found (or that the user confirmed despite a potential duplicate)

## Output

- The URL of the newly created issue.
- Labels, milestone, and project assignment confirmed.
- Duplicate check result (none found, or user confirmed).
