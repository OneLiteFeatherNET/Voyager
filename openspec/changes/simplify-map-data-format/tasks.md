# Tasks

Every behaviour is written test first (Red, then Green, then refactor). Every test follows F.I.R.S.T.: no sleeps, no
wall-clock reads, no shared static state, filesystem access only through `@TempDir`, and one behaviour per test named
after it. Adapter tests parse JSON strings, so they need no filesystem at all. Commits follow the type per commit rule in
`openspec/config.yaml`: `feat(platform)` for the loader, schema and shipped data; `feat(map-converter)` for the writer;
`docs(reference)` for the reference page.

## 1. Red: map file format

- [ ] 1.1 Red: in `MapDefinitionAdapterTest` add `readsARingWithoutIndexAsItsPosition`, `refusesARingWhoseDeclaredIndexIsNotItsPosition` (names the file, the position and the declared index), `readsAMapWithoutWorldAsItsName`, `keepsAnExplicitWorldUnchanged`, `refusesABlankExplicitWorld`, and `loadsTheShippedRacecourseTheSameWithAndWithoutItsRingIndexes`. Verify: `./gradlew :voyager-platform:test --tests '*MapDefinitionAdapterTest'` compiles and fails on the new tests only, with assertion messages and not compile errors.
- [ ] 1.2 Red: in `MapDefinitionAdapterTest` add `readsAMapWithoutSchemaVersionAsVersionOne`, `readsSchemaVersionOne`, `refusesSchemaVersionZero`, `refusesFractionalSchemaVersion`, `refusesTextSchemaVersion`, `refusesSchemaVersionAboveTheSupportedMaximum` (message contains `2` and `1`). Add the same cases for cups in `JsonCupCatalogTest` or a new `CupDefinitionAdapterTest`, with `refusesCupSchemaVersionAboveTheSupportedMaximum`. Verify: the new tests fail; existing tests still pass.
- [ ] 1.3 Red: in `tools/map-converter` `CatalogWriterTest` add `writesSchemaVersionOneForMapsAndCups`, `writesEveryRingIndexExplicitly`, `writesTheWorldExplicitly`. Verify: `./gradlew :tools:map-converter:test` fails on the three new tests.

## 2. Green: map file format and version rule

- [ ] 2.1 Green: add `RingAdapter.read(JsonElement, int position)`: take `index` when present and refuse a mismatch with a `JsonParseException` naming the position and the declared index; otherwise use `position`. Make `MapDefinitionAdapter` call it in a loop over the `rings` array. Remove the `Ring.class` registration from `CatalogDirectory`. Verify: the 1.1 ring tests pass; `./gradlew :voyager-platform:test` is otherwise green.
- [ ] 2.2 Green: in `MapDefinitionAdapter`, when `world` is absent use `name`; keep the blank check for a present world. Verify: the 1.1 world tests pass and `MapDefinition`'s blank-world refusal still names the file through `CatalogDirectory`.
- [ ] 2.3 Green: add package-private `adapter/SchemaVersion` with `CURRENT = 1` and a `read(JsonObject, String what)` that returns 1 when absent, refuses non-whole, below-1 and above-`CURRENT` values with messages naming the field (and, above the maximum, both numbers). Call it from `MapDefinitionAdapter` and `CupDefinitionAdapter`. Verify: the 1.2 tests pass.
- [ ] 2.4 Green: in `CatalogWriter` (`tools/map-converter`) write `schemaVersion: 1` first in map and cup objects, and keep `index` and `world` explicit. Verify: the 1.3 tests pass and `./gradlew :tools:map-converter:test` is green.
- [ ] 2.5 Refactor: remove any duplicated message text between the map and cup adapters, use `String.formatted(...)` throughout (`openspec` project rule, `java-style` skill), and confirm `package-info.java` still covers the adapter package with `@NotNullByDefault`. Verify: `./gradlew :voyager-platform:test :voyager-fitness:test` green.

## 3. Red/Green: derivation keeps shipped data unchanged

- [ ] 3.1 Red: in `voyager-server` `CommittedMapDataTest` add `theCommittedMapDeclaresSchemaVersionOne`, `theCommittedCupDeclaresSchemaVersionOne`, `everyShippedFileResolvesItsSchemaPath` (the `$schema` value, resolved against the file, is an existing file whose name matches the file kind), and `theCommittedRacecourseKeepsItsExplicitWorld` (world equals `ElytraraceBlueAndRed`). Verify: the first three fail because the shipped files do not yet declare them; the world test passes already and is a regression guard.
- [ ] 3.2 Green: add `"$schema"` (relative path, decision 4) and `"schemaVersion": 1` to the top of `voyager-server/src/main/resources/maps/elytraraceblueandred.json` and `voyager-server/src/main/resources/cups/test_cup.json`. Change no other value. Verify: `./gradlew :voyager-server:test` green; `git diff` on the two files shows only the added lines.
- [ ] 3.3 Verify equivalence on the real file: run the 1.1 equivalence test and confirm it compares every `MapDefinition` field (not only ring count). Verify: the test's assertion covers the full record via `isEqualTo`.

## 4. Schema documents

- [ ] 4.1 Red: add `voyager-platform/src/test/java/.../catalog/schema/FileSchemaTest.java` with `committedMapValidatesAgainstMapSchema`, `committedCupValidatesAgainstCupSchema`, `ringWithoutRadiusIsRejectedNamingRadius`, `unknownRingTypeIsRejected`, `schemaVersionTwoIsOutOfRangeInTheMapSchema`, `cupModeOutsideTheEnumIsRejected`. The test loads the schema from the classpath and reads committed files through an absolute path that the test computes from the module directory. Verify: the tests fail because `map.schema.json` and `cup.schema.json` do not exist yet. Requires the approval in task 4.2 first; if declined, skip 4.1 and 4.2 and record that in the PR.
- [ ] 4.2 Ask the owner (AskUserQuestion, per CLAUDE.md) to approve one JSON Schema 2020-12 validator as a `testImplementation` dependency of `voyager-platform`. Candidate: `com.networknt:json-schema-validator`. Pin an exact version that was released at least two weeks before the day of application; do not use a range. Verify: the owner's answer is recorded in the PR body; the version and its release date are written next to the pin in `voyager-platform/build.gradle.kts`, with the reason, in the same style as the other pins there.
- [ ] 4.3 Green: write `voyager-platform/src/main/resources/schema/map.schema.json` and `cup.schema.json` (draft 2020-12, `$id` per decision 4, `$defs` for `vec3`, `ring`, `guidePoint`, `boostConfig`, `guideLine`). Required lists match the adapters exactly (map: `name`, `spawn`, `referenceTimeSeconds`, `boostConfig`, `guideLine`, `rings`; ring: `center`, `normal`, `radius`, `points`, `type`; cup: `name`, `mode`, `mapNames`); `index`, `world`, `schemaVersion`, `notes`, `$schema` optional; `radius` and `referenceTimeSeconds` exclusive minimum 0; `type` enum from `RingType`; `mode` enum from `GameMode`; `schemaVersion` integer, minimum 1, maximum 1; `additionalProperties: false` on the map, ring, guide point and cup objects. Verify: task 4.1 green.
- [ ] 4.4 Refactor: compare each schema's required and optional lists with the adapter code line by line and record the check in the PR body. Verify: no field read by an adapter is missing from the schema, and no schema field is absent from the adapter.

## 5. Documentation

- [ ] 5.1 Write `docs/reference/map-and-cup-files.md` (Diataxis reference: a table per file kind with field, type, required or derived, derivation rule, refusal message). Cover the `schemaVersion` rule, the `$schema` path, and how VS Code and IntelliJ pick up the schema. State that the loader ignores unknown keys and the schema does not. Name the test for each derivation rule. Verify: every field read by `MapDefinitionAdapter`, `RingAdapter` and `CupDefinitionAdapter` appears in the table, checked by grep against the adapter source.
- [ ] 5.2 Mark Q2 and Q3 of `docs/research/005-simpler-map-and-cup-setup.md` section 5.2 as implemented by this change, with the change name. Verify: `git diff` touches only that table's Q2 and Q3 rows.

## 6. Verification

- [ ] 6.1 Run `./gradlew build` from the repository root (both trees) and `./gradlew :voyager-fitness:test`. Verify: exit code 0.
- [ ] 6.2 Smoke boot as in change `switch-di-to-avaje-inject` task 8.2: shipped `maps/` and `cups/`, a copied world directory `ElytraraceBlueAndRed`. Verify: log shows `Opened world 'ElytraraceBlueAndRed'` with 35 rings and `Voyager started`, and no refusal.
- [ ] 6.3 Hand check of the refusals with a temporary data directory under `@TempDir` or a scratch copy (not the repository): a map with `schemaVersion: 2` and a ring with a wrong index. Verify: each refusal names the file and the field or value, once per file.

## 7. Pull request

- [ ] 7.1 Open the pull request against `main` with the title `feat(platform): let map files leave out what the loader can derive`. The body covers: the world default decision and the three options from design decision 2; the schema decision (editor-side, test-scope check, the approved validator and its pin); the before and after example; the test list per task; the build result from 6.1; the smoke result from 6.2; and the commit list by type. End the body with the referral footer `https://claude.ai/referral/m5Ak2Sa7aQ`. Verify: `gh pr view` shows the title and base `main`.

## Workflow follow-up

- Archive after merge with `docs(openspec): archive simplify-map-data-format`, unless the project's review asks for the archive in the implementation pull request.
- When `voyager-setup` writes maps (research 005 phase 1), it writes `schemaVersion` and may leave out `index` and `world`; check it against `docs/reference/map-and-cup-files.md`.
