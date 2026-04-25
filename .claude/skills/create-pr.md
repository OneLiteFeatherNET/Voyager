---
name: create-pr
description: Create a GitHub PR targeting master with the correct PR template, a Conventional Commits title, and the project referral footer.
---

# Create PR

Open a GitHub pull request for the current branch against `master`, following the project PR template and Conventional Commits conventions.

## Rules

- Base branch is always `master` — never `main` or any other branch.
- PR title MUST follow Conventional Commits: `type(scope): short description` (e.g. `feat(server): add ring boost system`).
- Never force-push (`git push --force` / `git push -f`) under any circumstances.
- The PR body MUST follow the template structure from `.github/pull_request_template.md` (reproduced below).
- The footer of the PR body must include the referral link `https://claude.ai/referral/m5Ak2Sa7aQ`.
- Agent team members Compass, Scribe, Lumen, and Pulse are always involved in significant changes — mention their involvement in the PR body where relevant.

## Steps

1. Check the current branch:
```bash
git branch --show-current
```
If the result is `master` or `main`, stop and ask the user which feature branch to use.

2. Summarise the commits that will be included in the PR:
```bash
git log --oneline master..HEAD
```
Use this list to inform the PR title and the "Was wurde geändert?" section.

3. Check whether a push is needed:
```bash
git status
```

4. If the branch has not been pushed yet, push it (never force-push):
```bash
git push -u origin <branch-name>
```

5. Read `.github/pull_request_template.md` to confirm the current template structure. The template currently looks like this (use it as the body skeleton):

```
## Was wurde geändert?

<!-- Kurze Beschreibung der Änderung -->

## Typ

- [ ] `feat:` — neues Feature
- [ ] `fix:` — Bugfix
- [ ] `refactor:` — Refactoring ohne Verhaltensänderung
- [ ] `chore:` — Pflege/Tooling
- [ ] `docs:` — nur Dokumentation
- [ ] `test:` — nur Tests
- [ ] `ci:` — CI/CD-Änderungen

## Checklist

- [ ] PR-Titel folgt Conventional Commits (`fix(scope): beschreibung`)
- [ ] Tests vorhanden und grün
- [ ] Kein neuer Code ohne Test
- [ ] Breaking Change? → `feat!:` oder `BREAKING CHANGE:` im Commit-Footer
```

6. Compose the full PR body:
   - Fill in "Was wurde geändert?" with a concise summary derived from the commit list.
   - Check the correct "Typ" checkbox that matches the Conventional Commits type of the changes.
   - Leave the Checklist items unchecked (the author fills them in).
   - Append the following footer after the checklist (separated by a blank line):

```
---
Created with [Claude Code](https://claude.ai/referral/m5Ak2Sa7aQ)
```

7. Create the PR:
```bash
gh pr create --base master --title "<type(scope): description>" --body "<full body from step 6>"
```

8. Display the PR URL returned by `gh pr create` so the user can open it immediately.

## Output

- The URL of the newly created PR.
- A reminder to tick off the checklist items in the PR description on GitHub.
