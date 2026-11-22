# Tasks

Commit types and scope: `feat(catalog)` for the API, platform, setup writer, schemas and shipped data; `feat(catalog)` for the
statistics migration (it changes what is stored); `docs` for `docs/` pages and the ADR; `test` only for test-only commits that
do not change behaviour. Each behaviour gets a failing test (Red) before its production code (Green). Every test follows
F.I.R.S.T.: temporary directories for catalogue files (`@TempDir`), no shared static catalogue, a fresh instance per test, no
wall-clock reads. The open questions in `design.md` are answered by the owner before section 3 starts. Task checkboxes are
ticked in the follow-up pull request, never in the code pull request.

## 1. Red: the key and the authors

- [ ] 1.1 Red: `CupDefinitionTest` and `MapDefinitionTest` assert that a definition built with a key returns it from `key()`, and that the key's namespace is `elytrarace` (`feat(catalog)`)
- [ ] 1.2 Red: `Author` test: a UUID in capitals is normalised to lower case, a name of seventeen characters is refused, and nine authors are refused (`feat(catalog)`)
- [ ] 1.3 Red: adapter test in `platform/catalog` with `@TempDir`: a version 1 file with `"name": "palmbeach"` loads with key `elytrarace:palmbeach` (`feat(catalog)`)
- [ ] 1.4 Red: adapter test: a version 1 flat name with capitals is refused, and the message names the file and `name` (`feat(catalog)`)
- [ ] 1.5 Red: adapter test: a version 2 file with `"key": "other:alpha_cup"` is refused, and the message names the file and `key` (`feat(catalog)`)
- [ ] 1.6 Red: adapter test: a version 2 map without `world` takes the key value as its world, and a version 1 map keeps its name (`feat(catalog)`)
- [ ] 1.7 Red: adapter test: a version 2 file that states both `key` and `name` is refused (`feat(catalog)`)
- [ ] 1.8 Red: `CatalogConsistency` test: a cup naming `elytrarace:missing_map` reports a problem naming both keys (`feat(catalog)`)
- [ ] 1.9 Red: `SchemaVersion` test: version 3 is refused with the maximum 2 named (`feat(catalog)`)
- [ ] 1.10 Red: `ApiPurityTest` rule: the API uses Adventure only through `net.kyori.adventure.key`, with a seeded violation in `ApiPurityRulesSeededTest` that the rule reports (`test`)
- [ ] 1.11 Red: `VOYAGER_CUP` test: `alpha_cup` and `elytrarace:alpha_cup` select the same cup (`feat(catalog)`)
- [ ] 1.12 Red: writer test: `MapDraftJsonWriter` writes `schemaVersion` 2, `key`, `displayName` and `authors` when set and omits them when not set (`feat(catalog)`)
- [ ] 1.13 Red: setup test: loading a map with a display name and two authors and saving it without edits keeps both (`feat(catalog)`)
- [ ] 1.14 Red: confirm 1.1 to 1.13 fail for the stated reason, and record the messages in the pull request

## 2. Green: the model, the reader and the writer

- [ ] 2.1 Green: add `voyager-api` dependency `net.kyori:adventure-key` (version from the existing Adventure resolution), and add the `Author` record under `net.elytrarace.voyager.api.race` with its `package-info.java` (`feat(catalog)`)
- [ ] 2.2 Green: replace `name` with `Key key` in `CupDefinition` and `MapDefinition`, add `List<Author> authors`, and keep the convenience constructors with their meaning (`feat(catalog)`)
- [ ] 2.3 Green: add `ApiPurityTest` rule and its seeded violation (`test`)
- [ ] 2.4 Green: version 2 and version 1 reading in `CupDefinitionAdapter`, `MapDefinitionAdapter` and `MapDraftAdapter`, and the `SchemaVersion.CURRENT` value 2 (`feat(catalog)`)
- [ ] 2.5 Green: author reading with the UUID and name rules, in an adapter helper shared by both kinds of file (`feat(catalog)`)
- [ ] 2.6 Green: `CatalogDirectory` keys by the key, `CatalogConsistency` resolves `mapNames` as keys, `UnresolvedCupException` names keys (`feat(catalog)`)
- [ ] 2.7 Green: `VOYAGER_CUP` and the operator arguments accept both forms through the configuration loader (`feat(catalog)`)
- [ ] 2.8 Green: `MapDraft` carries `displayName` and `authors`; `MapDraftJsonWriter` writes version 2; the draft key comes from `MapId` in one place (`feat(catalog)`)
- [ ] 2.9 Refactor: confirm each test fixture compiles through the convenience constructors, and remove any `name()` call that is no longer used in production code (`feat(catalog)`)

## 3. Schemas and shipped data

- [ ] 3.1 Red: schema test that a version 2 map with `key` validates against `map.schema.json`, and a version 1 file validates against `map-v1.schema.json` (`feat(catalog)`)
- [ ] 3.2 Green: write `map.schema.json` and `cup.schema.json` at version 2, and add `map-v1.schema.json` and `cup-v1.schema.json` (`feat(catalog)`)
- [ ] 3.3 Green: rewrite `cups/alpha_cup.json`, `cups/legacy_cup.json`, and the four maps to version 2 with the keys and explicit worlds of the design table; rename `elytraraceblueandred.json`, `palmbeach.json` and `dragonlobby.json` to `blue_and_red.json`, `palm_beach.json` and `dragon_lobby.json`, and keep `memoria.json` and both cup files (`feat(catalog)`)
- [ ] 3.4 Green: add `authors` to the shipped map files as the migration table of `proposal.md` lists them: TheMeinerLP on `blue_and_red`, Seelenretterin on `palm_beach`, `dragon_lobby` and `memoria`; no cup has authors (`feat(catalog)`)
- [ ] 3.5 Red then Green: `CatalogLoader` test over the shipped data: no problem, and the snapshot holds the six keys of the design table (`feat(catalog)`)

## 4. Statistics

- [ ] 4.1 Red: Flyway test over an in-memory or container database with rows written under the flat ids: the migration rewrites `map_key` and `cup_key` and keeps `layout_version` (`feat(catalog)`)
- [ ] 4.2 Green: add `V2__catalogue_keys.sql` under `voyager/persistence/src/main/resources/db/migration` (`feat(catalog)`)
- [ ] 4.3 Green: the completion writer and the stats reader use the key (`feat(catalog)`)

## 5. Golden masters and checks

- [ ] 5.1 Update the 14 files under `voyager/server/src/test/resources/config-golden/expected/` that print a cup or map id, and `run-matrix.sh`, to the key form (`test`)
- [ ] 5.2 Run `CupSessionGoldenMasterTest` and confirm that every golden file is byte for byte unchanged, or list each changed file in the pull request (`test`)
- [ ] 5.3 Run `./gradlew :voyager:fitness:test` and `./gradlew build` on both trees (`test`)

## 6. Docs and decision record

- [ ] 6.1 Move the ADR text of `design.md` to `docs/decisions/0040-catalogue-keys.md`, with status Proposed unless the owner accepts it (`docs`)
- [ ] 6.2 Document the version 2 file format, the keys and the authors in the catalogue file reference under `docs/reference/` (`docs`)
- [ ] 6.3 Update `docs/migration/status.md` where it says the rebuild's catalogue is identified by a flat id (`docs`)

## 7. Smoke boot

- [ ] 7.1 Build the server shadow jar, boot it in a scratch directory with the shipped data copied and `VOYAGER_CUP=alpha_cup`, and quote the "Voyager started" line in the pull request (`test`)

## 8. Ship

- [ ] 8.1 Open the code pull request under the title `feat(catalog): key maps and cups and record their authors`. Its description lists each changed golden file and the migration table, and ends with the referral line of the repository's pull request rule. Do not add a session link (`feat(catalog)`)
