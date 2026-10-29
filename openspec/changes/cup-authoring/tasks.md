# Tasks

Commit types and scope: `feat(setup)` for the cup id, the store, the writer, the editor, the command service and wrapper, the messages and their
tests; `refactor(catalog)` for extracting the atomic write (its own commit, no behaviour change); `refactor(setup)` for the shared
reply helper; `docs` for the `docs/` pages and the ADR move; `test` only for test-only commits that change no behaviour. Each
behaviour gets a failing test (Red) before its production code (Green). Every test follows F.I.R.S.T.: a `@TempDir` data directory
per test with its own `maps/` and `cups/`, a fresh `JsonCupStore`, `EditClaims` and `CupCommandService` per test, a fake `FileMover` for the failed
move, no shared catalogue, no sleeps, no system-time reads. This change starts only after `map-name-and-authors` (change 7) and
`single-builder-map-claims` (change 5) are merged. Open question 1 of `design.md` (a map twice in one cup) is answered by the owner before section 1
starts; the other decisions in `design.md` are settled. Task checkboxes are ticked in the follow-up pull request, never in the code pull request.

## 1. Red: the cup id, the file, the map check, the claims and the commands

- [ ] 1.1 Red: `CupIdTest` asserts that `CupId` accepts and refuses the same strings as `MapId`, including `../etc`, and that a 33-character id is refused (`feat(setup)`)
- [ ] 1.2 Red: `CupDefinitionJsonWriterTest` asserts version 2, then `key`, `displayName` (only when set), `mode`, `mapNames` in order, that no `authors` key is written, and that the notes strings it is given are written verbatim after `mapNames`, with no `notes` key when it is given none (`feat(setup)`)
- [ ] 1.3 Red: `JsonCupStoreTest` asserts that a saved cup is read back by the game's `CupDefinitionAdapter` with the same key, mode, map list and display name, and that the `cups` folder is created on the first save (`feat(setup)`)
- [ ] 1.4 Red: `JsonCupStoreTest` asserts that a save naming a map with no file in `maps/`, and one naming a map whose file does not parse, is refused with a `CupMapNotLoadableException` that lists each key, and that no file changes (`feat(setup)`)
- [ ] 1.5 Red: `JsonCupStoreTest` asserts that when the fake `FileMover` throws, the previous cup file keeps its bytes, no temporary file remains, and the caller receives a cup-write failure (`feat(setup)`)
- [ ] 1.6 Red: `JsonCupStoreTest` asserts that two `JsonCupStore` instances over one `@TempDir` (two setup servers) refuse the second save with `CupChangedException` when the first writer changed the file, the file keeping the first writer's bytes, while a file with the same values in another key order is accepted (`feat(setup)`)
- [ ] 1.7 Red: `JsonCupStoreTest` asserts that a save of a cup whose stored file states an `authors` key is refused with `CupAuthorsRefusedException`, that the exception names the file and the key `authors`, and that the file keeps its bytes (`feat(setup)`)
- [ ] 1.8 Red: `JsonCupStoreTest` asserts that a cup file written by the test with a `notes` array of two strings, whose mode is changed and which is then saved, still holds the same two strings in the same order, and that the cup read back has the new mode (`feat(setup)`)
- [ ] 1.9 Red: `CupEditorTest` asserts that `name` applies the `MapName` rules and `clear` removes the name, and that `mode` sets the mode (`feat(setup)`)
- [ ] 1.10 Red: `CupEditorTest` asserts that `add` appends or inserts at a 1-based position, that `remove` shifts the later maps up, and that `move` shifts the maps between `from` and `to` (`feat(setup)`)
- [ ] 1.11 Red: `CupEditorTest` asserts that removing the last map is refused, and that a position outside the list is refused with the range named (`feat(setup)`)
- [ ] 1.12 Red: `CupCommandServiceTest` asserts that `new` creates a cup with one map, saves it and gives the builder its claim, replies with the key, that an existing cup id is refused, and that a draft map is refused with the message that says what to do first (`feat(setup)`)
- [ ] 1.13 Red: `CupCommandServiceTest` asserts that `open` gives the builder the claim and prints the definition, that `open` of a cup another builder holds is refused and names the holder, that `open` of the cup the builder already holds changes nothing, and that `open` of a second cup releases the first claim only after the new one is granted (`feat(setup)`)
- [ ] 1.14 Red: `CupCommandServiceTest` asserts that each of `name`, `mode`, `map add`, `map remove` and `map move` is refused without the builder holding the claim, that the refusal tells the builder to open the cup first, that a claim held by another builder is named, and that the file is unchanged (`feat(setup)`)
- [ ] 1.15 Red: `CupCommandServiceTest` asserts that `unlock` releases a claim held by another builder only for a sender holding `voyager.setup.unlock`, and that without the node the claim stays and the file is unchanged (`feat(setup)`)
- [ ] 1.16 Red: `CupCommandServiceTest` asserts that the builder's disconnect releases the cup claim, so that another builder can then open the cup (`feat(setup)`)
- [ ] 1.17 Red: `CupCommandServiceTest` asserts that each edit subcommand saves the cup before it replies, and that a failed save leaves the previous file and replies with the save-failed key (`feat(setup)`)
- [ ] 1.18 Red: `CupCommandServiceTest` asserts that `open` lists each map with its 1-based position and marks a map that is not game-loadable (`feat(setup)`)
- [ ] 1.19 Red: `SetupPermissionTest` asserts that each `/cup` subcommand except `unlock` is refused for a sender without `voyager.setup.use`, and that `unlock` is refused for a sender without `voyager.setup.unlock`; in each case the cup file is unchanged (`feat(setup)`)
- [ ] 1.20 Red: `CupCommandTest` asserts that the `/cup` command has no `close` subcommand, so that `/cup close alpha_run` replies with the usage message and changes no claim (`feat(setup)`)
- [ ] 1.21 Red: `SetupBundleTest` asserts that every cup message key exists in both bundles with the same `<arg:N>` placeholders (`feat(setup)`)
- [ ] 1.22 Red: confirm 1.1 to 1.21 fail for the stated reason, and record the failure messages in the pull request (`test`)

## 2. Green: the store, the writer, the editor, the service and the wrapper

- [ ] 2.1 Refactor: extract the temporary-file, force and replace sequence of `JsonDraftStore.save` into `AtomicJsonFile` in `net.elytrarace.voyager.platform.catalog`, and make `JsonDraftStore` use it; `JsonDraftStoreTest` passes unchanged (`refactor(catalog)`)
- [ ] 2.2 Green: `CupId` in `net.elytrarace.voyager.api.cupsetup` with the map id's rule, and the exceptions `CupNotFoundException`, `CupAlreadyExistsException`, `CupWriteFailedException`, `CupChangedException`, `CupMapNotLoadableException`, `CupAuthorsRefusedException` and `InvalidCupIdException` in `net.elytrarace.voyager.api.cupsetup.exception`, with `package-info.java` (`feat(setup)`)
- [ ] 2.3 Green: `CupStore` port with `load`, `create` and `saveIfUnchanged` in `net.elytrarace.voyager.api.cupsetup`, with no plain save (`feat(setup)`)
- [ ] 2.4 Green: `CupDefinitionJsonWriter` in `net.elytrarace.voyager.platform.catalog.writer` in the key order of the design, writing no `authors` and the notes strings it is given verbatim (`feat(setup)`)
- [ ] 2.5 Green: `JsonCupStore` in `net.elytrarace.voyager.platform.catalog`: the `cups` folder, the id rule, the game-loadable check through `CatalogLoader.read` and `CatalogConsistency.unresolvedCupMaps`, the `authors` refusal, the guard `saveIfUnchanged` on the canonical JSON (with `CupChangedException`), the read of the stored `notes` after the guard, and the save through `AtomicJsonFile` (`feat(setup)`)
- [ ] 2.6 Green: `CupEditor` in `net.elytrarace.voyager.setup.cupsetup`, an abstract utility class like `DraftEditor`, reusing `MapName` from `net.elytrarace.voyager.setup.mapsetup` (`feat(setup)`)
- [ ] 2.7 Green: `CupCommandPort` (interface), `CupCommandService` (pure class), `BuilderRef` and `CupReply` in `net.elytrarace.voyager.setup.cupsetup`, with `package-info.java`. The service takes `EditClaims` (key `cup:<id>`), the `CupStore`, the `CupEditor` and the `PermissionPolicy`; it holds each builder's open cup in an instance field, and it imports no Minestom or Adventure type (`feat(setup)`)
- [ ] 2.8 Green: `CupCommand` in `net.elytrarace.voyager.setup.inject`, extending Minestom's `Command`, with `new`, `open`, `name`, `mode`, `map add`, `map remove`, `map move` and `unlock`, and no `close`. It converts senders and arguments into the port's calls and renders each `CupReply` with `SetupMessages`. It forwards the builder's disconnect to the service. Register it with the command manager in `SetupServer` next to `SetupCommands` (`SetupServer.java:152-158`) (`feat(setup)`)
- [ ] 2.9 Green: add the message keys `voyager.setup.cup.created`, `voyager.setup.cup.exists`, `voyager.setup.cup.unknown`, `voyager.setup.cup.not-loadable`, `voyager.setup.cup.invalid-id`, `voyager.setup.cup.invalid-mode`, `voyager.setup.cup.renamed`, `voyager.setup.cup.mode-set`, `voyager.setup.cup.map-added`, `voyager.setup.cup.map-removed`, `voyager.setup.cup.map-moved`, `voyager.setup.cup.position-range`, `voyager.setup.cup.last-map`, `voyager.setup.cup.opened`, `voyager.setup.cup.map-line`, `voyager.setup.cup.map-line.missing`, `voyager.setup.cup.held`, `voyager.setup.cup.open-first`, `voyager.setup.cup.unlocked`, `voyager.setup.cup.not-held`, `voyager.setup.cup.authors-refused`, `voyager.setup.cup.changed-on-disk` and `voyager.setup.cup.save-failed` to `SetupMessages` and both bundles, German in informal "du" (`feat(setup)`)
- [ ] 2.10 Green: make the claim tests of section 1 (1.13 to 1.16) pass with change 5's `EditClaims` as it is merged; this change makes no change to `BuilderSessions` (`feat(setup)`)
- [ ] 2.11 Green: confirm every test of section 1 now passes, and that `./gradlew :voyager:api:test :voyager:platform:test :voyager:setup:test` is green (`test`)
- [ ] 2.12 Fitness: confirm that `SliceBoundaryRulesTest`, `PermissionBoundaryTest` and `NullabilityConventionTest` pass without any change to them, and that no file under `voyager/fitness/src/test/resources/archunit_store` changes in this pull request (`test`)

## 3. Refactor and docs

- [ ] 3.1 Refactor: the save-and-reply steps of the `/cup` command service share one helper; no behaviour change, the tests of section 1 unchanged (`refactor(setup)`)
- [ ] 3.2 Docs: add the section "Build a cup" to `docs/guides/how-to-build-a-map-with-setup.md`, covering `/cup new`, `open`, `name`, `mode`, the map subcommands with 1-based positions, the claim and `/cup unlock`, and the copy step to the game server, with the maps copied first (`docs`)
- [ ] 3.3 Docs: update the cup part of `docs/reference/map-and-cup-files.md` with the setup commands, the 1-based positions, the note that a setup save keeps the `notes` array, and the note that a cup carries no authors and a file that states them is refused on save (`docs`)
- [ ] 3.4 Docs: move the text of ADR-0045 from `design.md` to `docs/decisions/0045-cup-files-from-the-setup-server.md` with the status the owner gives it, Proposed unless accepted (`docs`)
- [ ] 3.5 Run `./gradlew :voyager:fitness:test` and `./gradlew build` on both trees before the pull request is opened (`test`)

## 4. Ship

- [ ] 4.1 Open the code pull request under the title `feat(setup): create and edit cups`. Fill the sections of `.github/pull_request_template.md`: the change in the builder's terms, the type `feat:`, and the checklist. Quote the failure messages of task 1.22, and state that the wrapper sits in `setup.inject` because the r6 rule exempts that package and not the root package. End it with the referral line the repository's pull request rule asks for. Do not add a session link (`feat(setup)`)
