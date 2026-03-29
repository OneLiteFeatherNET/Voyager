---
name: commit
description: Create a conventional commit. Use after completing a task.
---

# Conventional Commit

Create a properly formatted conventional commit.

## Steps

1. Run `git status` and `git diff --stat` to see changes
2. Analyze the changes and determine the commit type:
   - `feat:` — New feature
   - `fix:` — Bug fix
   - `refactor:` — Code restructuring without behavior change
   - `test:` — Adding or fixing tests
   - `docs:` — Documentation changes
   - `ci:` — CI/CD changes
   - `chore:` — Build, dependency, tooling changes

3. Draft a concise commit message (imperative mood, < 72 chars)
4. Stage relevant files (specific files, NOT `git add .`)
5. Commit using HEREDOC format:
```bash
git commit -m "$(cat <<'EOF'
{type}: {short description}

{Optional body with details}
EOF
)"
```

## Rules
- NO Co-Author line
- Imperative mood ("add" not "added")
- First line < 72 characters
- Body explains WHY, not WHAT (the diff shows what)
- Stage specific files, never `git add -A`
