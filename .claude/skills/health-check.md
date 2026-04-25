---
name: health-check
description: Full project health check — build, tests, ArchUnit, import isolation, migration status. Produces a single GREEN/RED report.
---

# Project Health Check

Run a comprehensive health check across all modules and produce a single status report.
This skill replaces running /build, /test, /check-imports, and /migration-status separately.

## Steps

### 1. Full Build

```bash
JAVA_HOME=/home/themeinerlp/.sdkman/candidates/java/25.0.1-open PATH="/home/themeinerlp/.sdkman/candidates/java/25.0.1-open/bin:$PATH" ./gradlew build --no-daemon 2>&1 | tail -20
```

Record: BUILD SUCCESS or BUILD FAILED. If FAILED, capture the error lines (lines containing "error:" or "FAILED").

### 2. All Tests (including ArchUnit)

```bash
JAVA_HOME=/home/themeinerlp/.sdkman/candidates/java/25.0.1-open PATH="/home/themeinerlp/.sdkman/candidates/java/25.0.1-open/bin:$PATH" ./gradlew test --no-daemon 2>&1 | grep -E "PASSED|FAILED|ERROR|tests|ArchUnit"
```

Record: total passed, total failed, total errors. ArchUnit tests live in `server/src/test/java/net/elytrarace/arch/` and run as part of the normal test task.

### 3. Import Isolation — shared/ (must be zero)

Check that shared modules contain no platform-specific imports:

```bash
grep -rc "org.bukkit" shared/ --include="*.java" | grep -v ":0"
grep -rc "io.papermc" shared/ --include="*.java" | grep -v ":0"
grep -rc "net.minestom" shared/ --include="*.java" | grep -v ":0"
```

Any output line is a violation. No output means clean.

### 4. Import Isolation — server/ (must be zero)

Check that the server module contains no Bukkit/Paper imports:

```bash
grep -rc "org.bukkit" server/ --include="*.java" | grep -v ":0"
grep -rc "io.papermc" server/ --include="*.java" | grep -v ":0"
```

Also verify that server/ does not import plugin packages:

```bash
grep -rc "net.elytrarace.game" server/ --include="*.java" | grep -v ":0"
grep -rc "net.elytrarace.setup" server/ --include="*.java" | grep -v ":0"
```

### 5. Migration Status Summary

Read `docs/migration/status.md` and extract the milestone table. Count open items from the Open Items section.

Also collect source file counts:

```bash
find server/src/main -name "*.java" | wc -l
find server/src/test -name "*.java" | wc -l
```

## Output

Produce a report with the following sections. Mark each section OK or FAILED.

```
=== Voyager Health Check ===

Build:            OK | FAILED
                  (error summary if FAILED)

Tests:            X passed / Y failed / Z errors
                  (list failing test names if any)

Import isolation:
  shared/ Bukkit: OK | VIOLATIONS (list files)
  shared/ Paper:  OK | VIOLATIONS (list files)
  shared/ Minestom: OK | VIOLATIONS (list files)
  server/ Bukkit: OK | VIOLATIONS (list files)
  server/ Paper:  OK | VIOLATIONS (list files)
  server/ plugin imports: OK | VIOLATIONS (list files)

Migration:
  M1 Foundation:      Complete  (6/6)
  M2 Shared Cleanup:  <status>  (<n>/5)
  M3 Core Game:       <status>  (<n>/7)
  M4 Gameplay:        <status>  (<n>/7)
  M5 Polish & Deploy: <status>  (<n>/9)
  Server source files: <n> main / <m> test
  Open high-priority items: <count>

Overall: GREEN (all checks passed) | RED (see above)
```

Overall is GREEN only when Build is OK, all tests pass, and import isolation has zero violations. Migration status is informational and does not affect the GREEN/RED verdict.
