# Tasks

Commit per type: every `refactor(platform)` task lands in the `refactor(platform)` commit or commits; the documentation
task is its own `docs(architecture)` commit. Every behaviour starts with a failing F.I.R.S.T. test (Red), then the
smallest production change (Green), then cleanup (Refactor). Unit tests use `@TempDir` and no clock or network.

## 1. Snapshot value (platform)

- [x] 1.1 Red: `CatalogSnapshotTest` asserts that a snapshot built from one map and one cup answers `mapByName`, `cupByName`, `mapNames` and `cupNames`, and that the name sets are equal to the inputs. Run it and see it fail to compile.
- [x] 1.2 Green: add the `CatalogSnapshot` record in `platform.catalog` with a compact constructor that copies both maps into unmodifiable, insertion-ordered maps.
- [x] 1.3 Red: add a test that mutating `maps()` or `cups()` throws `UnsupportedOperationException`, and that the snapshot is unchanged when the source map passed to the constructor is later mutated. Fails until the copy exists.
- [x] 1.4 Green: make the test pass without changing the test.

## 2. One load point, valid directory (platform)

- [x] 2.1 Red: `CatalogLoaderTest` with `@TempDir` writes one valid map and one valid cup and asserts `CatalogLoader.load(dir)` returns a snapshot holding both by name. Fails: no loader.
- [x] 2.2 Green: add `CatalogLoader` (final, private constructor, static `load(Path)` and `read(Path)`) delegating to `CatalogDirectory.readAll` for `maps/` and `cups/`.
- [x] 2.3 Red: a test that a valid catalogue with two maps and no dangling reference produces the same definitions as `JsonMapCatalog` and `JsonCupCatalog` on the same directory. This is the behaviour-preservation check; it fails until the loader reads both directories.
  - Note: the comparison against the removed `JsonMapCatalog` and `JsonCupCatalog` cannot survive their deletion (5.2). The same behaviour is pinned field by field in `CatalogLoaderTest` (`readsEveryFieldOfADefinitionBack`, `keepsEveryMapInTheDirectoryApart`, `keepsTheRotationInFileOrder`), and the shipped directory in `CommittedMapDataTest`.
- [x] 2.4 Green: pass 2.3.

## 3. Problems as data (platform)

- [x] 3.1 Red: `CatalogLoaderTest.readReturnsEveryMalformedMapFileAsAProblem`: two malformed map files give a reading with two `CatalogProblem`s, sorted by file name, each whose `cause()` is a `MalformedCatalogFileException`. Nothing is thrown.
- [x] 3.2 Green: `CatalogDirectory.readAll` records per-file failures as `CatalogProblem` and continues; add `CatalogProblem` and `CatalogReading` (records) in `platform.catalog`.
- [x] 3.3 Red: `CatalogLoaderTest.missingCupsDirectoryIsOneProblemAndMapsAreStillRead`: `cups/` absent and `maps/` valid gives exactly one problem naming `cups/`, listed first (cups are read first, as boot does today), and the snapshot still holds the maps.
- [x] 3.4 Green: read both directories independently in `CatalogLoader.read`.
- [x] 3.5 Red: `CatalogLoaderTest.duplicateMapNameIsAProblemNamingBothFiles`: two files with the same name give one problem whose message names both paths and the name; two reads of the same directory give equal readings, and the problem is listed after every `cups/` problem.
- [x] 3.6 Green: pass 3.5 by keeping sorted order and recording the duplicate as a problem.
  - Note: 3.5 compares readings by source and message, as stated in its task.

## 4. Boot policy: the first problem refuses (platform)

- [x] 4.1 Red: `CatalogLoaderTest.loadRefusesWithTheFirstMalformedFileExceptionAndItsMessage`: two malformed map files; `load` throws `MalformedCatalogFileException` whose message equals today's message for the first file in sorted order. Add `loadRefusesWithTheCupFileWhenAMapAndACupAreBothMalformed`: the cup file is named, as boot names it today.
- [x] 4.2 Green: `CatalogLoader.load` calls `read`, then throws the `cause()` of the first problem in the order cups, maps, cross-catalogue check. Pass 4.1.
- [x] 4.3 Red: `CatalogLoaderTest.loadRefusesWithTodaysUnresolvedCupMapException`: a valid cup naming an unknown map; `load` throws `UnresolvedCupMapException` with today's message, listing the dangling entries.
- [x] 4.4 Red: `CatalogLoaderTest.crossCatalogueCheckIsSkippedWhenAMapFileIsMalformed`: a malformed map file and a cup naming a map; `load` throws the malformed-file exception, and no "plays unknown map" text appears anywhere in the thrown chain.
- [x] 4.5 Green: `CatalogConsistency.unresolvedCupMaps(maps, cups)` takes the two name-to-definition maps and returns an `Optional` holding today's single `UnresolvedCupMapException`; `CatalogLoader` runs it only when neither directory has a problem. Pass 4.3 and 4.4.

## 5. Retire the two catalogue classes (platform)

- [x] 5.1 Refactor: move each assertion in `JsonMapCatalogTest`, `JsonCupCatalogTest` and `CatalogConsistencyTest` to `CatalogLoaderTest` or `CatalogSnapshotTest`, one behaviour per test, and keep them green while both classes still exist.
- [x] 5.2 Refactor: delete `JsonMapCatalog`, `JsonCupCatalog` and their tests; `./gradlew :voyager-platform:test` is green.

## 6. Server wiring (server)

- [x] 6.1 Red: extend `VoyagerGraphTest` to assert that the graph holds exactly one `CatalogSnapshot`, that `MapCatalog` and `CupCatalog` resolve to answers that match the snapshot, and that a dangling cup fails graph construction with today's `UnresolvedCupMapException`. Fails until the beans are rewired.
  - Note: the dangling-cup refusal is asserted in `BootRefusalTest` (the boot path, `VoyagerServer.openGraph`), not in `VoyagerGraphTest`. `VoyagerGraphTest` asserts the single snapshot and the two ports.
- [x] 6.2 Green: in `ServerBeans`, replace the two JSON beans with `CatalogSnapshot catalog(@External ServerSettings)` calling `CatalogLoader.load`, add `MapCatalog` and `CupCatalog` beans as method references, and have the `cup` bean call `CupResolution.resolve(snapshot, cupName)` without a separate consistency call.
- [x] 6.3 Red: `CupResolutionTest` takes a snapshot; it fails to compile until the signature changes.
- [x] 6.4 Green: `CupResolution.resolve` takes `CatalogSnapshot`; use `cupNames()` and `cupByName` in place of the `JsonCupCatalog` methods.
- [x] 6.5 Refactor: update `CommittedMapDataTest` to load through `CatalogLoader`; update the `ServerBeans` and `ServerSettings` javadoc that names `JsonMapCatalog` or `CatalogConsistency`.

## 7. Verify architecture rules (fitness)

- [x] 7.1 Run `./gradlew :voyager-fitness:test`; expect green with no rule change. If a rule fails, stop and report the rule and the class; do not add or weaken a rule without approval.
- [x] 7.2 Confirm `CatalogLoader`, `CatalogReading`, `CatalogProblem` and `CatalogSnapshot` carry no DI annotation (ArchUnit or `grep` over `voyager-platform`).

## 8. Documentation

- [x] 8.1 Commit `docs(architecture)`: update the `catalog` row of the package table in `docs/explanation/architecture.md` (created by the sibling change; if absent, leave the row for that change and note it) and set research 005 roadmap Q1 to done, citing this change.
  - Done 2026-10-10: `docs/explanation/architecture.md` exists (from `define-clean-architecture-with-vertical-slices`); its `catalog` row names `CatalogLoader` as the one entry point and the removal of `JsonMapCatalog` and `JsonCupCatalog`. Research 005 roadmap Q1 is marked implemented by this change.

## 9. Verify and open the pull request

- [x] 9.1 Run `./gradlew build` over both trees and `openspec validate unify-catalog-loading --strict`; both must pass before the PR opens.
- [x] 9.2 (merged locally on 2026-10-10 per owner decision; no PR) Open the pull request with title `refactor(platform): load map and cup catalogues through one entry point`, body linking this change, and the referral footer required by project memory.
  - Deferred: owner merges locally first.
