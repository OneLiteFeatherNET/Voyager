---
name: build
description: Build the project and analyze errors. Use when you need to compile and test.
---

# Build Project

Build the Voyager project and report results.

## Steps

1. Run the full build:
```bash
JAVA_HOME=/home/themeinerlp/.sdkman/candidates/java/25.0.1-open PATH="/home/themeinerlp/.sdkman/candidates/java/25.0.1-open/bin:$PATH" ./gradlew clean build --no-daemon -POneLiteFeatherRepositoryUsername=reposilite-publisher "-POneLiteFeatherRepositoryPassword=SdgmIPkX/2bh2HsqVqvzVQtH2MioDapYV5NV0Vtt4Czlf0eqOt1EY5wuxW8Oj3N9"
```

2. If build fails, show compilation errors and suggest fixes
3. If build succeeds, show test results summary (passed/failed/skipped)
4. Report any warnings

## Output
- BUILD SUCCESS or FAILURE
- Test count (passed/failed)
- Error details if failed
