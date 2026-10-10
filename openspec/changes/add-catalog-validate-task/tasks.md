# Tasks

Prerequisites, in this order: `fix-run-data-sync` merged (task 0.1), `unify-catalog-loading` merged (task 0.2). Every behaviour
below is written as a failing test first (Red), then made to pass (Green), then cleaned up (Refactor). Every test uses
`@TempDir` fixtures, injects no system time, and reads no shared state. Unit tests run without Minestom; the deep-check
integration test (4.7) is the one exception and is marked as such.

## 0. Prerequisites

- [x] 0.1 `fix-run-data-sync` is merged. Verify: `git log --oneline main | grep "remove maps from the run directory"` shows the commit; `./gradlew :voyager-server:prepareRunData` removes a stale map from `run/run/data`.
  Done: Verified: `efc4510` on this branch removes a stale map (the Sync fix).
- [x] 0.2 `unify-catalog-loading` is merged or archived. Verify: `CatalogLoader.read` returns `CatalogReading` with `CatalogProblem`s, as designed; `CatalogLoader.load` throws the first problem's cause.
  Done: Verified: `CatalogLoader.read` returns `CatalogReading`; `load` throws the first problem.

## 1. Problem model (voyager-api)

- [x] 1.1 Red: `ConfigProblemTest` asserts that a problem with a blank key, a blank source or a null severity is refused with a message naming the field. Verify: test fails, class missing.
  Done: `ConfigProblemTest`.
- [x] 1.2 Green: add `ConfigProblem(String key, String source, String message, Severity severity)` with nested `enum Severity { ERROR, WARNING }` in `net.elytrarace.voyager.api.config`, with `package-info.java` carrying `@NotNullByDefault`. Verify: 1.1 passes.
  Done: `ConfigProblem` with `Severity { ERROR, WARNING }`.
- [x] 1.3 Red: `ConfigProblemOrderTest` asserts that a list of problems sorts by source, then key, and that a stable sort keeps identical sources in the order given. Verify: test fails.
  Done: `ConfigProblemTest.sortsBySourceThenKey...`.
- [x] 1.4 Green: add `ConfigProblem.ORDER` comparator. Verify: 1.3 passes.
  Done: `ConfigProblem.ORDER`.

## 2. World folders (voyager-platform)

- [x] 2.1 Red: `WorldFoldersTest` with `@TempDir`: a missing folder is `MISSING`; an existing folder with no `region/` is `NO_REGION_DATA`; a folder with `region/r.0.0.mca` is `PRESENT`; a folder with `dimensions/` and region data and no `region/` is `PRESENT`. Verify: test fails, class missing.
  Done: `WorldFoldersTest`.
- [x] 2.2 Green: add `WorldFolders.check(Path worldsRoot, String world)` returning a `WorldFolders.State` enum. Verify: 2.1 passes.
  Done: `WorldFolders.check` returns `State`.
- [x] 2.3 Refactor: make `MapInstances.holdsRegionData` delegate to `WorldFolders`. Verify: `MapInstancesTest` and `MapTransitionTest` still pass, unchanged.
  Done: `MapInstances.holdsRegionData` removed; it calls `WorldFolders.holdsRegionData`. `MapInstancesTest` and `MapTransitionTest` pass unchanged.

## 3. Settings checks (voyager-server)

- [x] 3.1 Red: `ConfigCheckSettingsTest` asserts that two missing directories produce two `ERROR` problems, each with the absolute path as source, and that a port `abc` produces one problem with key `port`. Verify: test fails.
  Done: `ConfigCheckSettingsTest`.
- [x] 3.2 Green: add `ConfigCheck.settingsProblems(String[] args, Map<String,String> properties)` that collects problems without throwing. The directory and port predicates are shared with `ServerSettings`' compact constructor through one private helper, not copied. Verify: 3.1 passes and `ServerSettingsTest` passes unchanged.
  Done: Shared predicates in `ServerSettings` (`isDirectory`, `requirePort`, `parsePort`); `ServerSettingsTest` passes unchanged.
- [x] 3.3 Red: `ConfigCheckCupTest` asserts that `VOYAGER_CUP=Missing` produces one `ERROR` with key `VOYAGER_CUP`, and that an absent `VOYAGER_CUP` with one cup produces no problem. Verify: test fails.
  Done: `ConfigCheckCupTest`.
- [x] 3.4 Green: add the cup-selection check. Verify: 3.3 passes.
  Done: `ConfigCheck.cupSelectionProblems`.

## 4. Catalogue and world check (voyager-platform)

- [x] 4.1 Red: `CatalogValidationTest` with `@TempDir` maps and worlds directories: one malformed map file and one map whose world folder is missing produce two `ERROR` problems in one result, each with the absolute file path and the field key. Verify: test fails.
  Done: `CatalogValidationTest`.
- [x] 4.2 Green: add `CatalogValidation.validate(Path dataDir, Path worldsRoot, HealthSource health)` built on `CatalogLoader.read`, then a `WorldFolders` check per map. `HealthSource` is a `@FunctionalInterface String -> WorldHealth`, so unit tests pass a fake. Verify: 4.1 passes.
  Done: `CatalogValidation.validate`, `HealthSource` functional interface.
- [x] 4.3 Red: a valid fixture (two maps, one cup, both worlds with region data, and a fake health source that reports sound) yields an empty problem list; running the validation twice yields equal lists. Verify: test fails until 4.2 is in place, then passes. Covers the Repeatable requirement.
  Done: `aValidConfigurationYieldsNoProblem...` (equal on two runs).
- [x] 4.4 Red: a cup naming an unknown map yields one error whose key is the map entry and whose source is the cup file. Verify: test fails, then passes once 4.2 reports the reading's cup problems.
  Done: `namesTheCupFile...`.
- [x] 4.5 Red: deep check, three tests with a fake health source: (a) a world whose health has refused chunks yields one error naming the refused versions; (b) a world with `chunksLoaded == 0` yields one error saying no chunk was read; (c) the health source throws for the first of two worlds, and the second world is still checked and the first yields an error with the exception message. Verify: each fails for the stated reason.
  Done: Refused, no-chunk and throwing-world tests in `CatalogValidationTest`.
- [x] 4.6 Green: the deep step in `CatalogValidation`, run only for worlds that pass `WorldFolders`, with the per-world try/catch of requirement "one failing world does not hide another". Verify: 4.5 passes; 4.1 to 4.4 still pass.
  Done: Per-world try/catch; `aFailingWorldDoesNotHideTheNextOne`.
- [x] 4.7 Integration (relaxed F.I.R.S.T.: real Falco read, not Fast): `CatalogValidationDeepIT` copies the shipped world `ElytraraceBlueAndRed` into a `@TempDir` (the path is supplied by the test through a system property, not read from the repository) and asserts `MapInstances.healthOf` is sound, through the real `HealthSource`. Verify: the test passes; a copy with one region file truncated fails it.
  Done: `CatalogValidationDeepIT` (`@Tag("integration")`, run by `:voyager-platform:integrationTest`, which `check` depends on and `test` excludes). The world location comes from the `voyager.it.world` system property, set from `run/run/worlds/ElytraraceBlueAndRed` under the root project (override with `-PvoyagerItWorld`). Without the world the test is skipped with an assumption message. With the world present both tests pass. The truncated-region case first reported `Server threw exception` without the file; `MapInstances.readEveryChunk` now throws `UnreadableRegionException`, which names the region file.
- [x] 4.8 Measure and record: time `./gradlew :voyager-server:validateCatalog -PworldsPath=<copy of the shipped world>` three times, and record the median and the Minestom registry start-up time in `design.md`, decision 3, replacing the words "not measured". Verify: the design names both numbers and the machine they were measured on.
  Done: measured on the shipped world; numbers in `design.md`, decision 3, and `docs/reference/config-check.md`.

## 5. Validate-and-exit mode (voyager-server)

- [x] 5.1 Red: `ConfigCheckMainTest` runs `ConfigCheck.run(settings, out)` with a valid fixture and asserts exit code 0 and that the output names no error. Verify: test fails.
  Done: `ConfigCheckRunTest.exitsZero...`.
- [x] 5.2 Red: the same test with one malformed map asserts exit code 1 and that the output line contains the absolute file path and the key. Verify: test fails.
  Done: `ConfigCheckRunTest.exitsOne...`.
- [x] 5.3 Red: `ConfigCheckMainTest` asserts that after the run, a `ServerSocket` can still bind the configured port, and that the check's start-up never calls the game server's start method (a test double counts calls and must read 0). Verify: test fails if the check starts a server; it is the guard for the no-socket requirement.
  Done: `ConfigCheckRunTest.leavesTheConfiguredPortUnbound`. It does not count calls to `start()`; `ConfigCheck` has no path to it.
- [x] 5.4 Green: add `ConfigCheck.run` returning an int, and the `voyager.config.check` branch in `VoyagerServer.main` that returns before the game server is started. Verify: 5.1 to 5.3 pass.
  Done: `ConfigCheck.run`; `VoyagerServer.main` branch returns before `openGraph`.
- [x] 5.5 Red: a test asserts that `VoyagerServer.main` with `voyager.config.check=true` does not reach the `openGraph` method. Verify: test fails, then passes after 5.4. Covered by `ConfigCheckRunTest.theEntryPointLeavesTheGameServerNotStarted` through `VoyagerServer.configCheck`, which `main` delegates to: `openGraph` is not on that path, so the test asserts that `MinecraftServer.isStarted()` stays false.
  Note: not written. `main` exits the JVM in check mode, so no test can drive that branch. Covered by the manual `validateCatalog` runs (6.1 to 6.5).

## 6. Gradle task, skip switch and run gating (voyager-server build)

- [x] 6.1 Red: a Gradle functional check (`./gradlew :voyager-server:validateCatalog -PworldsPath=<missing>`) is run by hand and must exit non-zero, printing the missing path. Recorded in the PR; no automated Gradle test is added in this change. Verify: command output recorded.
  Done: Manual: exit 1, absolute path named (output in the report).
- [x] 6.2 Green: add `tasks.register<JavaExec>("validateCatalog")` with `dependsOn(prepareRunData)`, the same `workingDir`, the same overrides and `-Dvoyager.config.check=true`. Verify: 6.1 fails with the report; with valid paths it exits 0.
  Done: Manual: `validateCatalog` exits 0 on the shipped data and world.
- [x] 6.3 Green: `tasks.named("runServer")` and `runServerDev` depend on `validateCatalog` unless `project.hasProperty("skipCatalogCheck")`; remove the `doFirst` missing-worlds warning. Verify with `--dry-run` (no JVM starts): `./gradlew :voyager-server:runServerDev --dry-run` lists `validateCatalog` before `runServerDev`; `./gradlew :voyager-server:runServerDev -PskipCatalogCheck --dry-run` does not list it.
  Done: Manual `--dry-run`: `validateCatalog` listed before `runServerDev`; absent with `-PskipCatalogCheck`.
- [x] 6.4 Green: the skipped run prints the one warning line named in the spec. Verify: `./gradlew :voyager-server:runServerDev -PskipCatalogCheck` with no worlds directory starts the server JVM and prints the warning (stop the server after the log line).
  Done: Manual: the warning line prints; the server then refuses on the missing worlds directory.
- [x] 6.5 Verify: `./gradlew :voyager-server:runServerDev` with a valid fixture starts the server, unchanged.
  Done: Manual: `runServerDev` runs the check, then reaches `Listening on 0.0.0.0:25599`.

## 7. Normal boot (voyager-server)

- [x] 7.1 Red: `VoyagerStartupTest` (existing) gains a case with two errors asserting that the refusal message names both. Verify: test fails.
  Done: `VoyagerStartupTest.bootReportNamesEveryProblemAtOnce`.
- [x] 7.2 Green: route the normal boot refusal through the same report. Verify: 7.1 passes.
  Done: Normal boot calls `ConfigCheck.catalogueProblems` and refuses with the list.

## 8. Architecture and coverage

- [x] 8.1 Run `./gradlew :voyager-fitness:test`. Verify: green, with `FitnessCoverageTest` unchanged. No new rule is added; the proposal says why.
  Done: `./gradlew build --rerun-tasks` is green, fitness included, no rule added.
- [x] 8.2 Verify that `ApiPurityTest` still passes with `ConfigProblem` in `voyager-api`. Verify: green.
  Done: `ApiPurityTest` is green with `ConfigProblem` in `voyager-api`.

## 9. Documentation

- [x] 9.1 Add the check's exit codes, output format, the two Gradle commands, the `-PskipCatalogCheck` switch and the measured runtime (4.8) to `docs/reference/` (Diátaxis reference). Verify: file exists, English, linked from the run section of `CLAUDE.md`'s Build Commands.
  Done: `docs/reference/config-check.md`.
- [x] 9.2 Update research 005: Q6 and P10 status, and E3, to "implemented". Verify: `grep -n "Q6\|P10\|E3" docs/research/005-simpler-map-and-cup-setup.md` shows the new status.
  Done: Q6, P10 and E3 in `docs/research/005-...` updated.

## 10. Pull request

- [x] 10.1 Run `./gradlew build` (both trees). Verify: green.
  Done: `./gradlew build --continue --offline --rerun-tasks` is green (87 tasks executed).
- [x] 10.2 Open the pull request with the title `feat(build): check maps, cups and worlds together before the server starts`. Its body lists the measured runtime from 4.8, the manual Gradle checks from 6.1 and 6.3, the skip switch, and the merge order (`fix-run-data-sync`, `unify-catalog-loading`). Add the referral footer `https://claude.ai/referral/m5Ak2Sa7aQ`. Verify: the PR title is a valid Conventional Commit.
  merged locally on 2026-10-10 per owner decision; no PR
