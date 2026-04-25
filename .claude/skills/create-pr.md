---
name: create-pr
description: Create a GitHub PR targeting main with labels, milestone, project assignment, related issue references, a Conventional Commits title, and the project referral footer.
---

# Create PR

Open a GitHub pull request for the current branch against `main`, following the project PR template and Conventional Commits conventions.

## Rules

- Base branch is always `main`.
- PR title MUST follow Conventional Commits: `type(scope): short description` (e.g. `feat(server): add ring boost system`).
- Never force-push (`git push --force` / `git push -f`) under any circumstances.
- The PR body MUST be written in **English** — never German, even though the template headings are in German.
- The PR body MUST follow the template structure from `.github/pull_request_template.md` (reproduced below).
- The footer of the PR body must include the referral link `https://claude.ai/referral/m5Ak2Sa7aQ`.
- Agent team members Compass, Scribe, Lumen, and Pulse are always involved in significant changes — mention their involvement in the PR body where relevant.

## Steps

1. Check the current branch:
```bash
git branch --show-current
```
If the result is `main`, stop and ask the user which feature branch to use.

2. Summarise the commits that will be included in the PR:
```bash
git log --oneline origin/main..HEAD
```
Use this list to inform the PR title and the "What changed?" section.

3. Check whether a push is needed:
```bash
git status
```

4. If the branch has not been pushed yet, push it (never force-push):
```bash
git push -u origin <branch-name>
```

5. Read `.github/pull_request_template.md` to confirm the current template structure. Use it as the body skeleton, but **write all content in English**:

```
## What changed?

<!-- Short description of the change -->

## Type

- [ ] `feat:` — new feature
- [ ] `fix:` — bug fix
- [ ] `refactor:` — refactoring without behaviour change
- [ ] `chore:` — maintenance/tooling
- [ ] `docs:` — documentation only
- [ ] `test:` — tests only
- [ ] `ci:` — CI/CD changes

## Checklist

- [ ] PR title follows Conventional Commits (`fix(scope): description`)
- [ ] Tests present and passing
- [ ] No new code without tests
- [ ] Breaking change? → `feat!:` or `BREAKING CHANGE:` in commit footer
```

6. Compose the full PR body in **English**:
   - Fill in "What changed?" with a concise summary derived from the commit list.
   - Check the correct "Type" checkbox that matches the Conventional Commits type of the changes.
   - Leave the Checklist items unchecked (the author fills them in).
   - Append the following footer after the checklist (separated by a blank line):

```
---
Generated with [Claude Code](https://claude.ai/referral/m5Ak2Sa7aQ)
```

7. Create the PR:
```bash
gh pr create --base main --title "<type(scope): description>" --body "<full body from step 6>"
```

8. Add labels. Fetch available labels first, then pick all that apply:
```bash
gh label list --repo OneLiteFeatherNET/Voyager
```
Label mapping by Conventional Commits type:
- `feat:` → `enhancement`
- `fix:` → `bug`
- `docs:` → `documentation`
- `chore:` / `ci:` → `technic`
- `refactor:` / `test:` → `cleanup` or `testing`
- Always add domain labels if applicable: `gameplay`, `database`, `infrastructure`, `setup`
- Add priority labels if known: `P0`, `P1`, `P2`
```bash
gh pr edit <number> --add-label "<label1>,<label2>"
```

9. Assign the correct milestone. Fetch available milestones first:
```bash
gh api repos/OneLiteFeatherNET/Voyager/milestones --jq '.[] | "\(.number)\t\(.title)"'
```
Pick the earliest active milestone the PR contributes to (e.g. `Alpha v0.1` before `Beta v0.2`):
```bash
gh pr edit <number> --milestone "<milestone title>"
```

10. Add the PR to the Voyager Roadmap project (project ID 19):
```bash
gh project item-add 19 --owner OneLiteFeatherNET --url "<pr-url>"
```

11. Find related open issues and reference them in the PR body. Search for issues whose topic overlaps with the changes:
```bash
gh issue list --repo OneLiteFeatherNET/Voyager --state open --limit 50
```
- If this PR **closes** an issue, add `Closes #N` to the PR body (GitHub will auto-close it on merge).
- If this PR **supports but does not close** an issue, add a "Related issues" section with plain `#N` references and a one-line explanation of the relationship.
- If no issues are related, omit the section entirely — do not add empty placeholders.

Update the PR body with the issue references:
```bash
gh pr edit <number> --body "<updated body with issue references>"
```

12. Display the final PR URL so the user can open it immediately.

## Output

- The URL of the newly created PR.
- Labels, milestone, and project applied.
- Any related issues referenced in the body.
- A reminder to tick off the checklist items in the PR description on GitHub.
