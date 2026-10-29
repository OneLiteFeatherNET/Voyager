# Tasks

Commit types and scope: `feat(setup)` for the commands, the editor, the port's use, the messages and the setup tests;
`refactor(catalog)` for moving the display-name limit to the API (its own commit, no behaviour change); `docs` for the
`docs/` pages; `test` only for test-only commits that change no behaviour. Each behaviour gets a failing test (Red) before
its production code (Green). Every test follows F.I.R.S.T.: a fake `PlayerLookup`, a direct executor in place of a worker
thread, a `JsonDraftStore` over `@TempDir`, a fresh `MapSession` and `BuilderSessions` per test, no sleeps, no network, no
system-time reads. The open questions in `design.md` (the lookup timeout, the author cache and the reserved word `clear`) are answered by the
owner before section 2 starts. Task checkboxes are ticked in the follow-up pull request, never in the code pull request.
Change 6 (`keep-draft-metadata-on-edit`) must be merged before section 1 starts. `show-colored-map-and-cup-names` must be merged
before section 4 starts, because the code ships after it.

## 1. Red: the name, the authors and the reference time

- [ ] 1.1 Red: `MapNameTest` asserts that the MiniMessage source of a name is kept as written, that an unknown tag is refused with its position, and that a parse error is refused with its position (`feat(setup)`)
- [ ] 1.2 Red: `MapNameTest` asserts that a tag outside the display-name tag set, such as `<click:run_command:/stop>`, is refused with the tag named (`feat(setup)`)
- [ ] 1.3 Red: `MapNameTest` asserts that a text of 41 visible characters is refused with the 40-character limit, and that tags are not counted: `<gradient:#00c2ff:#5bff8f>Blue and Red Rush</gradient>` (17 visible characters) is accepted (`feat(setup)`)
- [ ] 1.4 Red: `MapNameTest` asserts that `clear` and a text with no visible characters both remove the name, and that `<white>clear` is stored as the name (`feat(setup)`)
- [ ] 1.5 Red: `MetadataEditorTest` asserts that setting a name, adding an author and setting the reference time keep the spawn, the rings, the boost configuration, the guide line and the ring effects (`feat(setup)`)
- [ ] 1.6 Red: `MetadataEditorTest` asserts that a ninth author and a repeated UUID are refused, and that removing an author matches the name ignoring case (`feat(setup)`)
- [ ] 1.7 Red: `ReferenceTimeTest` asserts that `45.5` and `3600` are accepted, and that `0`, `-1`, `3601`, `NaN` and `Infinity` are refused with the accepted range named (`feat(setup)`)
- [ ] 1.8 Red: `AuthorNameTest` asserts that a name of seventeen characters and a name with `../` are refused before any lookup, using a fake `PlayerLookup` that records calls (`feat(setup)`)
- [ ] 1.9 Red: `SetupCommandsTest` asserts that `/map author add` for an online player adds that player's UUID and name and makes no lookup call (`feat(setup)`)
- [ ] 1.10 Red: `AuthorLookupTest` asserts that an offline name is resolved through the fake lookup, that the returned UUID and name are stored, and that the lookup runs on the worker and the change on the tick (`feat(setup)`)
- [ ] 1.11 Red: `AuthorLookupTest` asserts that a lookup that finds no player, fails, or times out leaves the draft unchanged and writes no file, and that the builder is told which case applied (`feat(setup)`)
- [ ] 1.12 Red: `AuthorLookupTest` asserts that a result arriving after the builder opened another map changes neither map and tells the builder (`feat(setup)`)
- [ ] 1.13 Red: `SetupCommandsTest` asserts that `/map time 0` and `/map time NaN` change nothing and that `/map time 45.5` saves and replies with the value (`feat(setup)`)
- [ ] 1.14 Red: `SetupPermissionTest` asserts that each of `/map name`, `/map author add`, `/map author remove` and `/map time` is refused for a sender without `voyager.setup.use`, makes no lookup, and changes no file (`feat(setup)`)
- [ ] 1.15 Red: `SetupCommandsTest` asserts that a metadata change whose write fails keeps the previous draft in memory and replies with the save-failed message (`feat(setup)`)
- [ ] 1.16 Red: `SetupBundleTest` asserts that every metadata message key exists in `voyager_setup_en_US.properties` and `voyager_setup_de_DE.properties` with the same `<arg:N>` placeholders (`feat(setup)`)
- [ ] 1.17 Red: a round-trip test over `JsonDraftStore` in `@TempDir` sets a name and two authors through the commands, reloads the draft, and reads the saved file with `MapDefinitionAdapter`, which must give the same name and authors (`feat(setup)`)
- [ ] 1.18 Red: confirm 1.1 to 1.17 fail for the stated reason, and record the failure messages in the pull request (`test`)

## 2. Green: the editor, the parsers, the port and the commands

- [ ] 2.1 Green: add `DISPLAY_NAME_VISIBLE_LIMIT` (40 code points of visible text) in `net.elytrarace.voyager.api.race`, or reuse the constant that `show-colored-map-and-cup-names` adds if it has landed; `JsonFields` is not changed by this change (`feat(setup)`)
- [ ] 2.2 Green: `MapName` parser in `net.elytrarace.voyager.setup.mapsetup`: strict MiniMessage, the display-name tag set, the 40-visible-character limit, and `clear` and blank removal (`feat(setup)`)
- [ ] 2.3 Green: `ReferenceTime` parser in `net.elytrarace.voyager.setup.mapsetup`, finite, above zero, at most 3600 (`feat(setup)`)
- [ ] 2.4 Green: `MetadataEditor` in `net.elytrarace.voyager.setup.mapsetup`, an abstract utility class with a private constructor and `@ApiStatus.Internal`, with `withDisplayName`, `withAuthorAdded`, `withAuthorRemoved` and `withReferenceTime`, each built with the canonical `MapDraft` constructor (`feat(setup)`)
- [ ] 2.5 Green: `PlayerLookup` port with `Author resolve(String name)` in `net.elytrarace.voyager.api.mapsetup`, and its exceptions `PlayerNotFoundException`, `PlayerLookupTimeoutException` and `PlayerLookupFailedException` in `net.elytrarace.voyager.api.mapsetup.exception`, with `package-info.java` (`feat(setup)`)
- [ ] 2.6 Green: `MojangProfileLookup` in `net.elytrarace.voyager.platform.profile` (new package, `package-info.java` with `@NotNullByDefault`), JDK `HttpClient`, the 5-second timeout, the UUID dashed, a 404 mapped to `PlayerNotFoundException` (`feat(setup)`)
- [ ] 2.7 Green: `AuthorLookup` in `net.elytrarace.voyager.setup.adapter` uses no Minestom type; it runs the port on a worker, hands the result back to the tick thread through a main-thread interface that `SetupServer` implements, and applies it only when the builder's open session is the same `MapSession` instance. `SetupServer` provides the online-player check (`SliceBoundaryRulesTest.r6` stays clean) (`feat(setup)`)
- [ ] 2.8 Green: `SetupCommands` gains `name` (with `clear`), `author add`, `author remove` and `time`, each through `gated` and each saving through `MapSession.commit` (`feat(setup)`)
- [ ] 2.9 Green: add the message keys `voyager.setup.name.set`, `voyager.setup.name.cleared`, `voyager.setup.name.invalid`, `voyager.setup.name.tag-not-allowed`, `voyager.setup.name.too-long`, `voyager.setup.author.added`, `voyager.setup.author.removed`, `voyager.setup.author.unknown`, `voyager.setup.author.full`, `voyager.setup.author.repeated`, `voyager.setup.author.invalid-name`, `voyager.setup.author.lookup-failed`, `voyager.setup.author.lookup-timeout`, `voyager.setup.author.not-open`, `voyager.setup.time.set` and `voyager.setup.time.invalid` to `SetupMessages` and to both bundles, German in informal "du" (`feat(setup)`)
- [ ] 2.10 Green: the fitness rule that the new lookup runner is covered by `noBlockingOnFuturesOnTheTick`, if the owner accepts the proposed rule change in `design.md`; otherwise record the decision in the pull request (`test`)
- [ ] 2.11 Green: confirm that every test in section 1 now passes, and that `./gradlew :voyager:setup:test :voyager:platform:test :voyager:api:test` is green (`test`)

## 3. Refactor and docs

- [ ] 3.1 Refactor: the four command bodies share one save-and-reply helper; no behaviour change, the tests of section 1 unchanged (`refactor(setup)`)
- [ ] 3.2 Docs: add the section "Name and credit the map" to `docs/guides/how-to-build-a-map-with-setup.md`, covering `/map name`, `clear`, the `<white>clear` form, the tags a name may use, `/map author add` and `remove`, `/map time`, and what a failed lookup tells the builder (`docs`)
- [ ] 3.3 Docs: name the setup commands that write `displayName`, `authors` and `referenceTimeSeconds` in `docs/reference/map-and-cup-files.md`, under "Display names" and "Authors" (`docs`)
- [ ] 3.4 Run `./gradlew :voyager:fitness:test` and `./gradlew build` on both trees before the pull request is opened (`test`)

## 4. Ship

- [ ] 4.1 Open the code pull request under the title `feat(setup): set a map's name, authors and reference time`. Fill the sections of `.github/pull_request_template.md`: the change in the builder's terms, the type `feat:`, and the checklist. Quote the failure messages of task 1.18. End it with the referral line the repository's pull request rule asks for. Do not add a session link (`feat(setup)`)
