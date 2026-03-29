---
name: migration-status
description: Show the current Paper-to-Minestom migration status. Use to track progress.
---

# Migration Status

Show the current state of the Paper-to-Minestom migration.

## Steps

1. Count files and tests:
```bash
find server/src/main -name "*.java" | wc -l
find server/src/test -name "*.java" | wc -l
```

2. Check for remaining Bukkit imports in shared/:
```bash
grep -rc "org.bukkit" shared/ --include="*.java" | grep -v ":0"
```

3. Count test results:
```bash
./gradlew :server:test --no-daemon 2>&1 | grep -cE "PASSED|FAILED"
```

4. Show git log summary:
```bash
git log --oneline feat/minestom-migration-planning ^develop | wc -l
```

5. Read and display docs/migration/status.md if it exists

## Output
- Commits on branch
- Source files / Test files count
- Tests passing / failing
- Remaining Bukkit imports in shared/
- Open items from migration plan
