# Tasks

Every behaviour is written test first (red, then green, then refactor). Commits follow the types in the proposal: `test(...)`
for red tests, `feat(setup)` for green code, `build(setup)`, `docs(...)`. The squash commit on `main` is `feat(setup)`.
Run `./gradlew :voyager-setup:test :voyager-platform:test :voyager-api:test :voyager-fitness:test` after each small step.

## 0. Owner checkpoints (before apply)

- [ ] 0.1 Ask the owner (AskUserQuestion) for O1 to O6 in `design.md`, with the recommendations. Verify: each answer is written into the Open questions table, and any answer that changes a spec or a decision is applied to the affected spec before task 2.1.
- [ ] 0.2 Ask the owner to accept or amend the proposal's follow-up list and the O2 removal gesture. Verify: the follow-up table in `design.md` matches the answer.

## 1. Spikes (no production code; each result is recorded in `docs/research/006-minestom-26-2-setup-spikes.md`)

- [ ] 1.1 Display transformation spike: on `2026.08.28-26.2`, spawn a `BlockDisplay` in a test `Env`, set rotation and scale from a quaternion, and read the transform back. Record the exact setter names and whether the transform survives a tick. Verify: the record names a transformation API; the spike test lives under `voyager-setup/src/spike` and is not part of `test`.
- [ ] 1.2 Input spike: confirm which Minestom 26.2 events fire for right-click with an item, left-click on air, and left-click on a block; confirm an item tag can identify the wand. Verify: the record names one event per gesture and states what cancelling each does.
- [ ] 1.3 Void world spike: create a template directory with `level.dat` and one empty region file, then check that `MapInstances` (`holdsRegionData`) and the Falco loader accept it. Verify: the record states the minimum content of `templates/void-world/` that passes.
- [ ] 1.4 Write `docs/research/006-minestom-26-2-setup-spikes.md` (English, Lumen's format) with the three results, the methodology, and the decision each one unblocks. Commit as `docs(research)`. Verify: the file names the blocked tasks 6.3, 8.1 and 7.6.

## 2. Decision record and status documents (before code)

- [ ] 2.1 Write `docs/decisions/0018-pose-placement-authoring-model-for-setup.md` in MADR 4.0 with status `proposed`. Options: pose placement, record one lap, fly-through capture, polyhedral FAWE in game format. It cites greenfield D6, D8, D10 and ADR-0016, and it names Polar and the FAWE inventory as open. Commit as `docs(adr)`. Verify: the MADR sections are present and the status reads `proposed` until the owner accepts it.
- [ ] 2.2 Update the status documents, commit as `docs(setup)`: `STATUS.md` (E6 "BLOCKED" becomes "UNBLOCKED 2026-10-09"; E6.0 checked with a reference to research 005 and section 7.4); `docs/requirements/user-stories-stufe-6.md` (US-6.01 status `erledigt`, with the evidence: research 005 and the owner decision; US-6.02 to US-6.05 from `blockiert` to `offen`, with the note that E6.2 waits for ADR-0018); the greenfield epics (E6.0 acceptance criteria ticked with the same evidence); a cross-reference in the greenfield design's "Setup" section to ADR-0018. Verify: `grep -rn "blockiert\|BLOCKED" STATUS.md docs/requirements/user-stories-stufe-6.md` shows no E6.0 entry still blocked.
- [ ] 2.3 Check that research spike X1 (FAWE operation inventory) is recorded in `docs/research/`. If not, keep US-6.01 at `offen` until the owner decides O6 (this is the one E6.0 criterion that research 005 leaves open). Verify: the status entry matches what the file system holds.

## 3. Module skeleton and architecture rules

- [ ] 3.1 `build(setup)`: add `include("voyager-setup")` in `settings.gradle.kts`; create `voyager-setup/build.gradle.kts` with `voyager.java-conventions`, `implementation(project(":voyager-api"))`, `implementation(project(":voyager-platform"))`, `libs.avaje.inject.runtime`, `annotationProcessor(libs.avaje.inject.processor)`, `libs.jakarta.inject`, `testImplementation(libs.avaje.inject.test)`, `testImplementation("net.minestom:testing:2026.08.28-26.2")`, `systemProperty("minestom.inside-test", "true")` for tests, and a `runSetupDev` JavaExec with working directory `run-setup`, main class `net.elytrarace.voyager.setup.SetupServer`, and JVM args matching `voyager-server`. Add `package-info.java` (`@NotNullByDefault`) to every package that will hold code. Verify: `./gradlew :voyager-setup:compileJava` succeeds.
- [ ] 3.2 `test(fitness)` (red): add the six rules from `design.md` section 8 to `ApiPurityTest` with `allowEmptyShould(false)`, each description naming `net.elytrarace.voyager.setup`, and narrow `onlyServerDependsOnDiContainer`. Verify: `./gradlew :voyager-fitness:test` fails only on the new rules and on `FitnessCoverageTest` (voyager-setup has sources but no entry).
- [ ] 3.3 `test(fitness)` (green): add `":voyager-setup" to "net.elytrarace.voyager.setup"` to `PACKAGE_PREFIX_BY_PROJECT` and `testImplementation(project(":voyager-setup"))` to `voyager-fitness/build.gradle.kts`. Verify: `FitnessCoverageTest` and the rules pass once classes exist; the rules must not pass vacuously, which `allowEmptyShould(false)` ensures.

## 4. API types (`voyager-api`, `api.mapsetup`)

- [ ] 4.1 `test(api)` (red): `MapIdTest` (accepts `skyfortress`, `a-1_b`; refuses `../x`, uppercase, empty, 33 characters, a leading hyphen) and `MapDraftTest` (refuses a blank world, a ring whose index differs from its position, a non-positive reference time; accepts zero rings and a null spawn). Verify: tests fail to compile or fail for the stated reason.
- [ ] 4.2 `feat(setup)` (green): implement `MapId`, `MapDraft`, `DraftStore`, and the five exceptions in `api.mapsetup.exception` with `package-info.java`. Verify: both test classes pass; `DesignRuleTest` and `ApiPurityTest` pass.

## 5. Pure ring logic (`voyager-setup`, `setup.mapsetup`)

- [ ] 5.1 `test(setup)` (red): `RingFromPoseTest` (eye becomes center; a look of length 2 is normalised; zero and NaN look refused), `RingPickerTest` (nearest of two crossed rings wins; rim inclusive; ring behind the origin not hit; ray parallel to the disc not hit; beyond reach not hit; empty list gives empty), `RingOrientationTest` (maps the base axis onto (1,0,0), (0,1,0), (0,0,-1), and the antipode of the base axis), `DraftEditorTest` (append sets index to count; removal renumbers later rings; spawn set; input record not mutated), `MapStatusTest` (empty draft lists two problems; full draft lists none), `RingDefaultsTest` (radius equals the sample's radius, `Math.sqrt(13)`). Verify: all new tests fail for the stated reason.
- [ ] 5.2 `feat(setup)` (green): implement `RingDefaults`, `RingFromPose`, `RingPicker`, `RingOrientation`, `DraftEditor`, `MapStatus`, and `InvalidPoseException` in `setup.mapsetup.exception`. Verify: `./gradlew :voyager-setup:test --tests '*mapsetup*'` passes in milliseconds; no test reads the clock.
- [ ] 5.3 `refactor(setup)`: remove duplication between `RingPicker` and `MapStatus` if any; keep the commit without behaviour change. Verify: tests still pass.

## 6. Storage (`voyager-platform`)

- [ ] 6.1 `test(platform)` (red): `MapDraftJsonWriterTest` (exact key order; `spawn` omitted when null; ring keys in order; `type` is the string name), `MapDraftJsonRoundTripTest`: a complete draft (spawn, two rings) written and then read through the existing `MapDefinitionAdapter`, `MapDraftAdapterTest` (missing `spawn` accepted; a missing ring key names the file and the key), `JsonDraftStoreTest` (`@TempDir`: `create` writes skeleton and refuses an existing folder; `save` replaces the file and leaves no temp file; with a `FileMover` that throws, the previous bytes stay and no temp file remains; `load` on a missing folder throws `DraftNotFoundException`). Verify: red for the stated reasons.
- [ ] 6.2 `feat(setup)` (green): implement `catalog.writer.MapDraftJsonWriter`, `catalog.adapter.MapDraftAdapter`, `catalog.JsonDraftStore` with the `FileMover` seam and the atomic order in `design.md` section 4. Verify: platform tests pass; `onlyPlatformDependsOnGson` and the platform DI rules still pass.
- [ ] 6.3 `test(platform)` then `feat(setup)`: `world.VoidWorldTemplate` copies `templates/void-world/` into `maps/<id>/world/` and refuses an existing target. The template content is the minimum from spike 1.3. Verify: a `@TempDir` test asserts the copied files; `MapInstances` accepts the copied world (from spike 1.3).

## 7. Server boot and commands (`voyager-setup`, Minestom `Env`)

- [ ] 7.1 `test(setup)` (red): `SetupSettingsTest` (default data path `run-setup/data`; `VOYAGER_DATA_PATH` used when set), `SetupServerTest` (a missing data directory is reported with its absolute path and no start is attempted; the check is a static method that returns the problem instead of calling `System.exit`), `SetupGraphTest` (a `BeanScope` built with a `@TempDir` data path resolves `DraftStore`, the session registry and the listeners; each test builds its own scope). Verify: red.
- [ ] 7.2 `feat(setup)` (green): implement `setup.config.SetupSettings`, `setup.SetupServer` (order as `VoyagerServer`, `shutdownHook(false)`, closed in a shutdown task), `setup.inject.SetupBeans` (`@Factory`, `@Bean` methods that call constructors). Verify: 7.1 passes; `SetupServer` is the only class outside `inject` that touches avaje.
- [ ] 7.3 `test(setup)` (red): `SetupCommandsTest` with an `Env` per test. `/map new skyfortress` creates `maps/skyfortress/map.json` and `world/`, opens the map and gives the wand; `/map new ../x` changes nothing; `/map new` on an existing folder changes nothing; `/map open missing` refuses; `/map spawn` saves the spawn; `/map status` replies with spawn state, ring count and problems. Verify: red; no `Thread.sleep`; time is driven by `env.tick()`.
- [ ] 7.4 `feat(setup)` (green): implement `setup.adapter.SetupCommands` (Minestom `Command` builder, as `RaceCommand`), `setup.adapter.MapSession`, and the user-facing messages through `platform.text.Messages` with a setup bundle. Verify: 7.3 passes; `onlyTheTextPackageBuildsAUserFacingString` passes.
- [ ] 7.5 `test(setup)` (red): `WandListenerTest` with an `Env`: three right-clicks give three rings in `map.json` with indices 0, 1, 2 and the default radius; a zero look vector adds nothing; a left-click through ring 1 of three removes it and renumbers the file; a left-click with no ring in reach changes nothing and says so; a `DraftStore` whose `save` throws keeps the previous draft in memory and in the file. Verify: red.
- [ ] 7.6 `feat(setup)` (green): implement `setup.adapter.WandListener` with the events fixed by spike 1.2, and the item identity from spike 1.2. Verify: 7.5 passes.
- [ ] 7.7 `test(setup)` (red) then `feat(setup)` (green): a block break or placement by a builder in an open map is cancelled (`setup/ring-placement`, Should). Verify: a test asserts the block stays and the event is cancelled.

## 8. Previews (after spike 1.1)

- [ ] 8.1 `test(setup)` (red): `RingPreviewTest` with an `Env`: opening a three-ring map shows three previews at the ring centers; a placement adds one; a removal removes one and renumbering keeps the count equal to the ring count; `map.json` contains no entity data. Verify: red. This task does not start until the record of task 1.4 names the transformation API.
- [ ] 8.2 `feat(setup)` (green): implement `setup.adapter.RingPreview` with the API named by spike 1.1 and `RingOrientation` from task 5.2. Verify: 8.1 passes; previews are removed when the map is closed.

## 9. End to end and documentation

- [ ] 9.1 `test(setup)` (red then green): one `Env` test runs the builder's path: `/map new`, three right-clicks, one left-click, `/map spawn`, `/map status`; then the file holds two rings, a spawn, and the status reports zero problems. Verify: passes without sleeps.
- [ ] 9.2 `docs(guides)`: write `docs/guides/how-to-build-a-map-with-setup.md` (Diataxis how-to) with the commands, the wand gestures, the default radius, the draft folder, and the note that terrain is built in the external editor. Verify: every command in the page is run once on the setup server during 10.2.
- [ ] 9.3 `docs(setup)`: update `STATUS.md` and the user stories to the state after this change (E6.1 and the ring part of E6.2 done; E6.2 FAWE operations and E6.4 open). Verify: the status matches `design.md`'s follow-up table.

## 10. Verification

- [ ] 10.1 Run `./gradlew build` (both trees) and the module tests named in the proposal. Verify: green; no `Thread.sleep`, `System.currentTimeMillis`, `System.nanoTime` or `Instant.now` in the new tests (`grep`).
- [ ] 10.2 Boot check: `./gradlew :voyager-setup:runSetupDev`, then `/map new`, three placements and one removal from a client. Run it once with `VOYAGER_DATA_PATH` pointing to a missing directory and confirm the refusal names the path. Verify: the log lines are attached to the PR description.
- [ ] 10.3 Module isolation check: `grep -rn "org.bukkit\|net.elytrarace.voyager.server" voyager-setup/src` returns nothing. Verify: the output is empty.
- [ ] 10.4 `openspec validate introduce-voyager-setup --strict`. Verify: exit 0.

## 11. Pull request

- [ ] 11.1 Open the pull request with the title `feat(setup): start a minestom setup server that places rings where the builder looks`. The body lists the commits by type, the spike record, the open questions answered (O1 to O6), the boot log of 10.2, and the follow-up table. End the body with the footer `https://claude.ai/referral/m5Ak2Sa7aQ`. Verify: `gh pr view` shows the title; the title passes commitlint.
- [ ] 11.2 After the squash merge, archive with `docs(openspec): archive introduce-voyager-setup` unless the archive ships with the implementation. Verify: `openspec/changes/archive/` holds the change directory.
