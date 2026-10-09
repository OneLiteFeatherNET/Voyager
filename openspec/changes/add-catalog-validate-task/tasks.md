# Tasks

Prerequisite: `unify-catalog-loading` is merged. Every behaviour below is written as a failing test first (Red), then made to pass (Green), then cleaned up (Refactor). Every test uses `@TempDir` fixtures, injects no system time, and reads no shared state. Unit tests run without Minestom.

## 1. Problem model (voyager-api)

- [ ] 1.1 Red: `ConfigProblemTest` asserts that a problem with a blank key, a blank source or a null severity is refused with a message naming the field. Verify: test fails, class missing.
- [ ] 1.2 Green: add `ConfigProblem(String key, String source, String message, Severity severity)` with nested `enum Severity { ERROR, WARNING }` in `net.elytrarace.voyager.api.config`, with `package-info.java` carrying `@NotNullByDefault`. Verify: 1.1 passes.
- [ ] 1.3 Red: `ConfigProblemOrderTest` asserts that a list of problems sorts by source, then key, and that a stable sort keeps identical sources in the order given. Verify: test fails.
- [ ] 1.4 Green: add `ConfigProblem.ORDER` comparator. Verify: 1.3 passes.

## 2. World folders (voyager-platform)

- [ ] 2.1 Red: `WorldFoldersTest` with `@TempDir`: a missing folder is `MISSING`; an existing folder with no `region/` is `NO_REGION_DATA`; a folder with `region/r.0.0.mca` is `PRESENT`; a folder with `dimensions/` and region data and no `region/` is `PRESENT`. Verify: test fails, class missing.
- [ ] 2.2 Green: add `WorldFolders.check(Path worldsRoot, String world)` returning a `WorldFolders.State` enum. Verify: 2.1 passes.
- [ ] 2.3 Refactor: make `MapInstances.holdsRegionData` delegate to `WorldFolders`. Verify: `MapInstancesTest` and `MapTransitionTest` still pass, unchanged.

## 3. Settings checks (voyager-server)

- [ ] 3.1 Red: `ConfigCheckSettingsTest` asserts that two missing directories produce two `ERROR` problems, each with the absolute path as source, and that a port `abc` produces one problem with key `port`. Verify: test fails.
- [ ] 3.2 Green: add `ConfigCheck.settingsProblems(String[] args, Map<String,String> properties)` that collects problems without throwing. The directory and port predicates are shared with `ServerSettings`' compact constructor through one private helper, not copied. Verify: 3.1 passes and `ServerSettingsTest` passes unchanged.
- [ ] 3.3 Red: `ConfigCheckCupTest` asserts that `VOYAGER_CUP=Missing` produces one `ERROR` with key `VOYAGER_CUP`, and that an absent `VOYAGER_CUP` with one cup produces no problem. Verify: test fails.
- [ ] 3.4 Green: add the cup-selection check. Verify: 3.3 passes.

## 4. Catalogue and world check (voyager-platform)

- [ ] 4.1 Red: `CatalogValidationTest` with `@TempDir` maps and worlds directories: one malformed map file and one map whose world folder is missing produce two `ERROR` problems in one result, each with the absolute file path and the field key. Verify: test fails.
- [ ] 4.2 Green: add `CatalogValidation.validate(Path dataDir, Path worldsRoot)` built on the aggregate loader from `unify-catalog-loading`, then a `WorldFolders` check per map. Verify: 4.1 passes.
- [ ] 4.3 Red: a valid fixture (two maps, one cup, both worlds with region data) yields an empty problem list; running the validation twice yields equal lists. Verify: test fails until 4.2 is in place, then passes. Covers the Repeatable requirement.
- [ ] 4.4 Red: a cup naming an unknown map yields one error whose key is the map entry and whose source is the cup file. Verify: test fails, then passes once 4.2 reports the aggregate loader's cup problems.

## 5. Validate-and-exit mode (voyager-server)

- [ ] 5.1 Red: `ConfigCheckMainTest` runs `ConfigCheck.run(settings, out)` with a valid fixture and asserts exit code 0 and that the output names no error. Verify: test fails.
- [ ] 5.2 Red: the same test with one malformed map asserts exit code 1 and that the output line contains the absolute file path and the key. Verify: test fails.
- [ ] 5.3 Red: `ConfigCheckMainTest` asserts that after the run, a `ServerSocket` can still bind the configured port, and that the check's start-up never calls the server bootstrap (a test double for the bootstrap counts calls and must read 0). Verify: test fails if the check starts a server; it is the guard for the no-socket requirement.
- [ ] 5.4 Green: add `ConfigCheck.run` returning an int, and the `voyager.config.check` branch in `VoyagerServer.main` that returns before any Minestom class is loaded. Verify: 5.1 to 5.3 pass.
- [ ] 5.5 Red: a test asserts that `VoyagerServer.main` with `voyager.config.check=true` does not reach the `openGraph` method. Verify: test fails, then passes after 5.4.

## 6. Gradle task and run gating (voyager-server build)

- [ ] 6.1 Red: a Gradle functional check (`./gradlew :voyager-server:validateCatalog -PworldsPath=<missing>`) is run by hand and must exit non-zero, printing the missing path. Recorded in the PR; no automated Gradle test is added in this change. Verify: command output recorded.
- [ ] 6.2 Green: add `tasks.register<JavaExec>("validateCatalog")` with `dependsOn(prepareRunData)`, the same `workingDir`, the same overrides and `-Dvoyager.config.check=true`. Verify: 6.1 fails with the report; with valid paths it exits 0.
- [ ] 6.3 Green: make `runServer` and `runServerDev` depend on `validateCatalog`; remove the `doFirst` missing-worlds warning. Verify: `./gradlew :voyager-server:runServerDev` with no worlds directory stops before the server JVM starts, printing the report.
- [ ] 6.4 Verify: `./gradlew :voyager-server:runServerDev` with a valid fixture starts the server, unchanged.

## 7. Normal boot (voyager-server)

- [ ] 7.1 Red: `VoyagerStartupTest` (existing) gains a case with two errors asserting that the refusal message names both. Verify: test fails.
- [ ] 7.2 Green: route the normal boot refusal through the same report. Verify: 7.1 passes.

## 8. Architecture and coverage

- [ ] 8.1 Run `./gradlew :voyager-fitness:test`. Verify: green, with `FitnessCoverageTest` unchanged. No new rule is added; the proposal says why.
- [ ] 8.2 Verify that `ApiPurityTest` still passes with `ConfigProblem` in `voyager-api`. Verify: green.

## 9. Documentation

- [ ] 9.1 Add the check's exit codes, output format and the two Gradle commands to `docs/reference/` (Diátaxis reference). Verify: file exists, English, linked from the run section of `CLAUDE.md`'s Build Commands.
- [ ] 9.2 Update research 005: Q6 and P10 status, and E3, to "implemented". Verify: `grep -n "Q6\|P10\|E3" docs/research/005-simpler-map-and-cup-setup.md` shows the new status.

## 10. Pull request

- [ ] 10.1 Run `./gradlew build` (both trees). Verify: green.
- [ ] 10.2 Open the pull request with the title `feat(build): check maps, cups and worlds together before the server starts`. Its body lists the open questions from `design.md`, the manual Gradle check from 6.1, and the `fix-run-data-sync` follow-up. Add the referral footer `https://claude.ai/referral/m5Ak2Sa7aQ`. Verify: the PR title is a valid Conventional Commit.
