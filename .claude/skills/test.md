---
name: test
description: Run tests for a specific module or all modules and summarize results.
---

# Run Tests

Run tests and provide a clear summary.

## Steps

1. Determine scope from user input (default: `:server:test`)
2. Run tests:
```bash
JAVA_HOME=/home/themeinerlp/.sdkman/candidates/java/25.0.1-open PATH="/home/themeinerlp/.sdkman/candidates/java/25.0.1-open/bin:$PATH" ./gradlew :server:clean :server:test --no-daemon
```
3. Count PASSED, FAILED, SKIPPED
4. For failures: show test name + error message
5. Suggest fixes for failing tests

## Output
- Total: X passed, Y failed, Z skipped
- Failed test details (if any)
- Suggested fixes
