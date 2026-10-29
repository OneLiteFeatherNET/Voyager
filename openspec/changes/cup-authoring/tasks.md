# Tasks

Commit types and scope: `feat(setup)` for the cup id, the store, the writer, the editor, the commands, the messages and their
tests; `refactor(catalog)` for extracting the atomic write (its own commit, no behaviour change); `refactor(setup)` for the shared
reply helper; `docs` for the `docs/` pages and the ADR move; `test` only for test-only commits that change no behaviour. Each
behaviour gets a failing test (Red) before its production code (Green). Every test follows F.I.R.S.T.: a `@TempDir` data directory
per test with its own `maps/` and `cups/`, a fresh `JsonCupStore` and `CupCommands` per test, a fake `FileMover` for the failed
move, no shared catalogue, no sleeps, no system-time reads. This change starts only after `map-name-and-authors` (change 7) and
`single-builder-map-claims` (change 5) are merged. The open questions in `design.md` are answered by the owner before section 1
starts. Task checkboxes are ticked in the follow-up pull request, never in the code pull request.

## 1. Red: the cup id, the file, the map check and the commands

- [ ] 1.1 Red: `CupIdTest` asserts that `CupId` accepts and refuses the same strings as `MapId`, including `../etc`, and that a 33-character id is refused (`feat(setup)`)
- [ ] 1.2 Red: `CupDefinitionJsonWriterTest` asserts version 2, then `key`, `displayName` (only when set), `mode`, `mapNames` in order, and `authors` (only when set), and that no `notes` key is written (`feat(setup)`)
- [ ] 1.3 Red: `JsonCupStoreTest` asserts that a saved cup is read back by the game's `CupDefinitionAdapter` with the same key, mode, map list, display name and authors, and that the `cups` folder is created on the first save (`feat(setup)`)
- [ ] 1.4 Red: `JsonCupStoreTest` asserts that a save naming a map with no file in `maps/`, and one naming a map whose file does not parse, is refused with a `CupMapNotLoadableException` that lists each key, and that no file changes (`feat(setup)`)
- [ ] 1.5 Red: `JsonCupStoreTest` asserts that when the fake `FileMover` throws, the previous cup file keeps its bytes, no temporary file remains, and the caller receives a cup-write failure (`feat(setup)`)
- [ ] 1.6 Red: `JsonCupStoreTest` asserts that an existing cup with a display name and one author keeps both after its mode is changed and it is saved; and that two `JsonCupStore` instances over one `@TempDir` (two setup servers) refuse the second save with `CupChangedException` when the first writer changed the file, the file keeping the first writer's bytes, while a file with the same values in another key order is accepted (`feat(setup)`)
- [ ] 1.7 Red: `CupEditorTest` asserts that `name` applies the `MapName` rules and `clear` removes the name, and that `mode` sets the mode (`feat(setup)`)
- [ ] 1.8 Red: `CupEditorTest` asserts that `add` appends or inserts at a 1-based position, that `remove` shifts the later maps up, and that `move` shifts the maps between `from` and `to` (`feat(setup)`)
- [ ] 1.9 Red: `CupEditorTest` asserts that removing the last map is refused, and that a position outside the list is refused with the range named (`feat(setup)`)
- [ ] 1.10 Red: `CupCommandsTest` asserts that `/cup new` creates a cup with one map and replies with the key, that an existing cup id is refused, and that a draft map is refused with the message that says what to do first (`feat(setup)`)
- [ ] 1.11 Red: `CupCommandsTest` asserts that each `/cup map` subcommand saves the cup before it replies, and that a failed save leaves the previous file and tells the builder (`feat(setup)`)
- [ ] 1.12 Red: `CupCommandsTest` asserts that `/cup info` lists each map with its 1-based position and marks a map that is not game-loadable (`feat(setup)`)
- [ ] 1.13 Red: `SetupPermissionTest` asserts that each `/cup` subcommand is refused for a sender without `voyager.setup.use`, and that the cup file is unchanged (`feat(setup)`)
- [ ] 1.14 Red: `SetupBundleTest` asserts that every cup message key exists in both bundles with the same `<arg:N>` placeholders (`feat(setup)`)
- [ ] 1.15 Red, only when open question 1 of `design.md` is answered with option A: `CupCommandsTest` asserts that a second builder's edit of a cup that the first builder has open is refused, and names the first builder (`feat(setup)`)
- [ ] 1.16 Red: confirm 1.1 to 1.15 (those that apply) fail for the stated reason, and record the failure messages in the pull request (`test`)

## 2. Green: the store, the writer, the editor and the commands

- [ ] 2.1 Refactor: extract the temporary-file, force and replace sequence of `JsonDraftStore.save` into `AtomicJsonFile` in `net.elytrarace.voyager.platform.catalog`, and make `JsonDraftStore` use it; `JsonDraftStoreTest` passes unchanged (`refactor(catalog)`)
- [ ] 2.2 Green: `CupId` in `net.elytrarace.voyager.api.cupsetup` with the map id's rule, and the exceptions `CupNotFoundException`, `CupAlreadyExistsException`, `CupWriteFailedException`, `CupMapNotLoadableException` and `InvalidCupIdException` in `net.elytrarace.voyager.api.cupsetup.exception`, with `package-info.java` (`feat(setup)`)
- [ ] 2.3 Green: `CupStore` port (`load`, `create`, `save`) in `net.elytrarace.voyager.api.cupsetup` (`feat(setup)`)
- [ ] 2.4 Green: `CupDefinitionJsonWriter` in `net.elytrarace.voyager.platform.catalog.writer` in the key order of the design (`feat(setup)`)
- [ ] 2.5 Green: `JsonCupStore` in `net.elytrarace.voyager.platform.catalog`: the `cups` folder, the id rule, the game-loadable check through `CatalogLoader.read` and `CatalogConsistency.unresolvedCupMaps`, the save guard `saveIfUnchanged` on the canonical JSON (with `CupChangedException`), and the save through `AtomicJsonFile` (`feat(setup)`)
- [ ] 2.6 Green: `CupEditor` in `net.elytrarace.voyager.setup.cupsetup`, an abstract utility class like `DraftEditor`, reusing `MapName` from `net.elytrarace.voyager.setup.mapsetup` (`feat(setup)`)
- [ ] 2.7 Green: `CupCommands` in `net.elytrarace.voyager.setup.adapter`: `new`, `name`, `mode`, `map add`, `map remove`, `map move` and `info`, each gated through the policy and each saving through the store before it replies (`feat(setup)`)
- [ ] 2.8 Green: register `CupCommands` with the command manager in `SetupServer`, next to `SetupCommands` (`SetupServer.java:152-158`) (`feat(setup)`)
- [ ] 2.9 Green: add the message keys `voyager.setup.cup.created`, `voyager.setup.cup.exists`, `voyager.setup.cup.unknown`, `voyager.setup.cup.not-loadable`, `voyager.setup.cup.invalid-id`, `voyager.setup.cup.invalid-mode`, `voyager.setup.cup.renamed`, `voyager.setup.cup.mode-set`, `voyager.setup.cup.map-added`, `voyager.setup.cup.map-removed`, `voyager.setup.cup.map-moved`, `voyager.setup.cup.position-range`, `voyager.setup.cup.last-map`, `voyager.setup.cup.info`, `voyager.setup.cup.info.missing` and `voyager.setup.cup.save-failed` to `SetupMessages` and both bundles, German in informal "du" (`feat(setup)`)
- [ ] 2.10 Green: if open question 1 is answered with option A, add `/cup open <id>` and `/cup close` with the claim of change 5's registry, and make 1.15 pass (`feat(setup)`)
- [ ] 2.11 Green: confirm every test of section 1 that applies now passes, and that `./gradlew :voyager:api:test :voyager:platform:test :voyager:setup:test` is green (`test`)
- [ ] 2.12 Fitness: `CupCommands` extends Minestom's `Command` in `setup.adapter`, which is a new violation of the frozen rule `SliceBoundaryRulesTest.r6_setupAdapterDoesNotUseMinestom`. Add its freeze-store entry in this pull request and name it in the pull request description, so the owner sees the debt being added (`test`)

## 3. Refactor and docs

- [ ] 3.1 Refactor: the `/map` and `/cup` command bodies share one save-and-reply helper; no behaviour change, the tests of section 1 unchanged (`refactor(setup)`)
- [ ] 3.2 Docs: add the section "Build a cup" to `docs/guides/how-to-build-a-map-with-setup.md`, covering `/cup new`, `name`, `mode`, the map subcommands with 1-based positions, `info`, and the copy step to the game server, with the maps copied first (`docs`)
- [ ] 3.3 Docs: update the cup part of `docs/reference/map-and-cup-files.md` with the setup commands, the 1-based positions, and the note that a setup save does not write `notes` (`docs`)
- [ ] 3.4 Docs: move the text of ADR-0045 from `design.md` to `docs/decisions/0045-cup-files-from-the-setup-server.md` with the status the owner gives it, Proposed unless accepted (`docs`)
- [ ] 3.5 Run `./gradlew :voyager:fitness:test` and `./gradlew build` on both trees before the pull request is opened (`test`)

## 4. Ship

- [ ] 4.1 Open the code pull request under the title `feat(setup): create and edit cups`. Fill the sections of `.github/pull_request_template.md`: the change in the builder's terms, the type `feat:`, and the checklist. Name the freeze-store entry of task 2.12 and the failure messages of task 1.16. End it with the referral line the repository's pull request rule asks for. Do not add a session link (`feat(setup)`)
