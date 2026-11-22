# Tasks

Commit type and scope: `feat(race)` for the api, platform and server code, its tests and the seeded catalogue. `docs` for
`docs/` pages. Each behaviour gets a failing test (Red) before its production code (Green). Every test follows F.I.R.S.T.:
temp directories for catalogue files (`@TempDir`), no shared static catalogue, a fresh snapshot per test. Task checkboxes
are ticked in the follow-up PR, never in the code PR.

## 1. Red: the record and the loader

- [ ] 1.1 Red: `CupDefinitionTest` and `MapDefinitionTest` assert that a definition without a display name returns its id from `displayName()`, and that a blank display name returns the id (`feat(race)`)
- [ ] 1.2 Red: adapter test "a cup file with displayName keeps it" and "a map file with displayName keeps it" in `platform/catalog` with `@TempDir` files (`feat(race)`)
- [ ] 1.3 Red: adapter test "a display name longer than forty characters refuses the file, and the error names displayName and the file" (`feat(race)`)
- [ ] 1.4 Red: schema test "a cup or map with a string displayName validates against its schema" for both `cup.schema.json` and `map.schema.json` (`feat(race)`)
- [ ] 1.5 Red: confirm 1.1 to 1.4 fail for the stated reason, and record the messages in the PR

## 2. Green: the record and the loader

- [ ] 2.1 Green: add `displayName` as the last component of `CupDefinition` and `MapDefinition` in `voyager/api`, with the compact constructor that stores the id when the value is null or blank; keep the three- and seven-argument constructors (`feat(race)`)
- [ ] 2.2 Green: read `displayName` in `CupDefinitionAdapter` and `MapDefinitionAdapter`, and refuse a value longer than 40 characters with the field and file in the message (`feat(race)`)
- [ ] 2.3 Green: add `displayName` (type string, optional) to `cup.schema.json` and `map.schema.json` (`feat(race)`)
- [ ] 2.4 Refactor: confirm no test fixture needs an edit, since the convenience constructors keep their signatures (`feat(race)`)

## 3. Red: every player-facing surface shows the name

- [ ] 3.1 Red: a test that the chat banner for "elytraraceblueandred" names "Blue & Red" (`CupSession`/`CupAnnouncer`) (`feat(race)`)
- [ ] 3.2 Red: a test that the race boss bar title (`RaceHud.java:203`), the cup heading and the map and cup results name the display name (`feat(race)`)
- [ ] 3.3 Red: a test that the lobby board for "alpha_cup" gives the title "Alpha Cup" and the map line "Blue & Red", using the snapshot to resolve the map id (`feat(race)`)
- [ ] 3.4 Red: a test that a name "<rainbow>Blue</rainbow>" is shown literally in the banner (`feat(race)`)
- [ ] 3.5 Red: a test that a German-locale player and an English-locale player receive the same name text for "legacy_cup" (`feat(race)`)
- [ ] 3.6 Red: confirm the failing messages and record them in the PR

## 4. Green: thread the name through the render paths

- [ ] 4.1 Green: pass `displayName()` to `Messages.bossBarTitle` (`RaceHud.java:203`), `Messages.mapBanner`, `cupHeading`, `mapResult`, `mapResultDnf` and `finished` in `CupSession`, `CupAnnouncer` and `RaceHud` (`feat(race)`)
- [ ] 4.2 Green: change `LobbyBoard.of(cup)` to `LobbyBoard.of(cup, snapshot)`; resolve each map id to the map's display name, keeping the id when the map is missing from the snapshot. Update `WaitingRoom.java:305` and `:325` (`feat(race)`)
- [ ] 4.3 Green: pass name arguments as `Component.text`, so MiniMessage does not read a `<` in a name (`feat(race)`)
- [ ] 4.4 Refactor: remove the now unused `name()` uses on cups and maps in player-facing code, leaving operator output unchanged (`feat(race)`)

## 5. Seed the shipped catalogue

- [ ] 5.1 Red: a test "the shipped catalogue names its cups and maps" that loads `voyager/server` shipped data and asserts the six names in the spec (`feat(race)`)
- [ ] 5.2 Green: add `displayName` to `cups/alpha_cup.json`, `cups/legacy_cup.json`, `maps/elytraraceblueandred.json`, `maps/palmbeach.json`, `maps/dragonlobby.json` and `maps/memoria.json`, with the values in the spec (`feat(race)`)
- [ ] 5.3 Green: remove the sentence in `cups/alpha_cup.json` note `:10` that says the name is recorded there until a schema change adds one (`feat(race)`)

## 6. Golden masters and checks

- [ ] 6.1 Run `CupSessionGoldenMasterTest` and confirm that every golden file is byte for byte unchanged (`feat(race)`)
- [ ] 6.2 Run the config golden tests and confirm the operator output still names ids (`feat(race)`)
- [ ] 6.3 Run `./gradlew :voyager:fitness:test` and `./gradlew build` on both trees (`feat(race)`)

## 7. Docs

- [ ] 7.1 Update `docs/migration/status.md` lines 150 and 176, which say the rebuild has no display-name field, to say the field exists and where the names come from (`docs`)
- [ ] 7.2 Document the optional `displayName` field in the catalogue file reference under `docs/reference/` (find the page that lists `mapNames` or `notes`) (`docs`)

## 8. Ship

- [ ] 8.1 Open the code pull request under the title `feat(race): show display names for cups and maps`. Put the golden check result (6.1) in the description and write it as the player sees the change. Do not add a session link (`feat(race)`)
