# Proposal

**Conventional Commits:** `fix(build)`. Squash-merge PR title: `fix(build): remove maps from the run directory once they leave the catalogue`.

Slice: none (build logic). The change touches `voyager-server/build.gradle.kts` only, the `prepareRunData` task. No Java, no catalogue file format, no game behaviour.

## Why

`prepareRunData` copies the shipped catalogue (`voyager-server/src/main/resources/maps` and `cups`) into `run/run/data`
with Gradle's `Copy`. `Copy` adds and overwrites files but never removes one. A map deleted from the repository therefore
stays in `run/run/data` and keeps being served by the game server on the next run. The check of `add-catalog-validate-task`
reads the same directory, so it would report the stale map as valid. Research 005 and the owner's decision of 2026-10-09
ask for the fix to land before that check.

## What Changes

- `prepareRunData` uses `Sync` instead of `Copy`, with the same source and the same includes (`maps/**`, `cups/**`). After the
  task runs, `run/run/data` holds exactly the shipped `maps/` and `cups/` files, and no others.
- `run/run/data` is documented in the task description as a build-owned mirror: a file placed there by hand is removed by the
  next run. Operators put worlds in `run/run/worlds`, which this change does not touch.
- **BREAKING (developer workflow, run directory only):** a hand-made file in `run/run/data` disappears on the next run. No
  shipped behaviour and no file in the repository changes.

## Capabilities

### New Capabilities
- `build/run-data-sync`: the run directory's catalogue mirrors the shipped catalogue after each run of `prepareRunData`.

### Modified Capabilities
- None. `openspec/specs/` holds no build requirement today.

## Impact

- **Build:** `voyager-server/build.gradle.kts`, `prepareRunData` only. Its dependants (`runServer`, `runServerDev`, and
  `validateCatalog` once `add-catalog-validate-task` lands) are unchanged.
- **Tests:** none in the Java test tree. Verification is a reproducible Gradle run, recorded in the PR (design, decision 3).
- **Docs:** none. The task description states the behaviour.
- **Runtime:** none for the shipped server. The run directory is git-ignored (`.gitignore`, `/run/`).

## Out of Scope

- The `run/run/worlds` directory and the doubled `run/run` path (both kept as they are).
- The validate task (`add-catalog-validate-task`), which depends on this change.
- A Gradle TestKit test for the task. Possible later, if the owner wants a permanent automated guard; see design, decision 3.
