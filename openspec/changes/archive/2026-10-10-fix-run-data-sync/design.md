# Design

## Context

`voyager-server/build.gradle.kts` defines:

```kotlin
val runWorkingDir: File = rootProject.file("run")
val runDataDir: File = runWorkingDir.resolve("run/data")   // i.e. <root>/run/run/data with the doubled run path
val prepareRunData by tasks.registering(Copy::class) {
    from(layout.projectDirectory.dir("src/main/resources")) { include("maps/**"); include("cups/**") }
    into(runDataDir)
}
```

Gradle's `Copy` only adds and overwrites. `Sync` copies the same files and also deletes every file in the destination that the
copy spec does not produce. `run/` is git-ignored (`.gitignore`, `/run/`).

## Decisions

### 1. Sync, with the same spec

Replace `Copy` with `Sync`, keeping the source, the includes and the destination. Rejected: a `doFirst { delete(...) }` followed
by `Copy`. It does the same job, but it deletes before it copies, so a failed copy leaves an empty catalogue, and it needs two
steps to stay in step with the spec. `Sync` does the delete and the copy as one Gradle task with its own up-to-date check.

### 2. The destination is build-owned

`Sync` removes everything in `run/run/data` that the spec does not produce. The directory exists only as the build's mirror of
the shipped catalogue. This is the intended behaviour, and it is stated in the task description so that a hand-made file does not
vanish without a reason. Files an operator needs go in `run/run/worlds` (not touched) or in a directory outside `run/run/data`.

### 3. Verification: a reproducible Gradle sequence, not a TestKit test

Choice: the lightest reliable check. A Gradle TestKit test would run a nested Gradle build for one copy operation, costs tens of
seconds per run, and needs a fixture project that mirrors this build's layout. The task is one line of build logic. The check is
therefore a fixed command sequence run by hand before the PR opens, with its output recorded in the PR:

1. Red (on `main`'s build file): `./gradlew :voyager-server:prepareRunData`, then create `run/run/data/maps/stale.json`, run
   `./gradlew :voyager-server:prepareRunData` again, and confirm `stale.json` is still there. This reproduces the bug.
2. Green: apply the change, repeat step 1. `stale.json` must be gone after the second run, and `run/run/data/maps/elytraraceblueandred.json`
   and `run/run/data/cups/test_cup.json` must still exist and be byte-identical to the resources (`cmp`).
3. Up-to-date behaviour: a second run with no change reports `prepareRunData` as `UP-TO-DATE` or as a no-op sync.
   The deleted-resource case is the same as step 1: a file that no source produces is removed.

If the owner wants a permanent automated guard, a TestKit test is a follow-up change, not part of this fix.

### 4. Architecture and fitness

No Java code changes, so no fitness rule is touched. The change lives in the build script of the `voyager-server` module.

## Risks

- **Operators who kept files in `run/run/data`** lose them on the next run. Mitigation: the task description says so; the change
  is in the PR body as BREAKING (run directory only).
- **Sync with a missing source directory** fails rather than deleting everything. Mitigation: the source is `src/main/resources`,
  which always exists in the repository.

## Rollback

Revert the single squash commit. `prepareRunData` goes back to `Copy`, and stale files return as before.
