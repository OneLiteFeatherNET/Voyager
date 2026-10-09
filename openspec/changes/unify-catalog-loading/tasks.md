# Tasks

Commit per type: every `refactor(platform)` task lands in the `refactor(platform)` commit or commits; the documentation
task is its own `docs(architecture)` commit. Every behaviour starts with a failing F.I.R.S.T. test (Red), then the
smallest production change (Green), then cleanup (Refactor). Unit tests use `@TempDir` and no clock or network.

## 1. Snapshot value (platform)

- [ ] 1.1 Red: `CatalogSnapshotTest` asserts that a snapshot built from one map and one cup answers `mapByName`, `cupByName`, `mapNames` and `cupNames`, and that the name sets are equal to the inputs. Run it and see it fail to compile.
- [ ] 1.2 Green: add the `CatalogSnapshot` record in `platform.catalog` with a compact constructor that copies both maps into unmodifiable, insertion-ordered maps.
- [ ] 1.3 Red: add a test that mutating `maps()` or `cups()` throws `UnsupportedOperationException`, and that the snapshot is unchanged when the source map passed to the constructor is later mutated. Fails until the copy exists.
- [ ] 1.4 Green: make the test pass without changing the test.

## 2. One load point, valid directory (platform)

- [ ] 2.1 Red: `CatalogLoaderTest` with `@TempDir` writes one valid map and one valid cup and asserts `CatalogLoader.load(dir)` returns a snapshot holding both by name. Fails: no loader.
- [ ] 2.2 Green: add `CatalogLoader` (final, private constructor, static `load(Path)`) delegating to `CatalogDirectory.readAll` for `maps/` and `cups/`.
- [ ] 2.3 Red: a test that a valid catalogue with two maps and no dangling reference produces the same definitions as `JsonMapCatalog` and `JsonCupCatalog` on the same directory. This is the behaviour-preservation check; it fails until the loader reads both directories.
- [ ] 2.4 Green: pass 2.3.

## 3. Collect every problem (platform)

- [ ] 3.1 Red: test that two malformed map files produce one `InvalidCatalogException` whose message names both file paths, and whose suppressed causes are two `MalformedCatalogFileException`.
- [ ] 3.2 Green: `CatalogDirectory.readAll` records per-file failures and continues; add `InvalidCatalogException` in `platform.catalog.exception` with its `package-info.java` check, and have `CatalogLoader` throw it when problems exist.
- [ ] 3.3 Red: test that a missing `cups/` directory with a valid `maps/` reports exactly the missing directory as the problem, and that `maps/` is still read (its malformed file, if present, is also named).
- [ ] 3.4 Green: read both directories independently in `CatalogLoader`.
- [ ] 3.5 Red: test that a duplicate map name in two files names both files and the name, and that the same directory produces the same message on repeated loads.
- [ ] 3.6 Green: pass 3.5 by keeping sorted order and collecting the duplicate as a problem, not an abort.

## 4. Consistency folded into the load (platform)

- [ ] 4.1 Red: test that a cup naming an unknown map fails `CatalogLoader.load` with "cup '<cup>' plays '<map>'" in the message, and that a valid one loads.
- [ ] 4.2 Green: change `CatalogConsistency` to take the two name-to-definition maps and return the dangling references; `CatalogLoader` calls it after both directories read cleanly.
- [ ] 4.3 Red: test that a malformed cup file and a dangling reference in another cup are reported together in one exception, and that when `maps/` is broken the consistency check is skipped (no "plays unknown map" line appears).
- [ ] 4.4 Green: pass 4.3.

## 5. Retire the two catalogue classes (platform)

- [ ] 5.1 Refactor: move each assertion in `JsonMapCatalogTest`, `JsonCupCatalogTest` and `CatalogConsistencyTest` to `CatalogLoaderTest` or `CatalogSnapshotTest`, one behaviour per test, and keep them green while both classes still exist.
- [ ] 5.2 Refactor: delete `JsonMapCatalog`, `JsonCupCatalog` and their tests; `./gradlew :voyager-platform:test` is green.

## 6. Server wiring (server)

- [ ] 6.1 Red: extend `VoyagerGraphTest` to assert that the graph holds exactly one `CatalogSnapshot`, that `MapCatalog` and `CupCatalog` resolve to answers that match the snapshot, and that a dangling cup fails graph construction with `InvalidCatalogException`. Fails until the beans are rewired.
- [ ] 6.2 Green: in `ServerBeans`, replace the two JSON beans with `CatalogSnapshot catalog(@External ServerSettings)` calling `CatalogLoader.load`, add `MapCatalog` and `CupCatalog` beans as method references, and have the `cup` bean call `CupResolution.resolve(snapshot, cupName)` without a separate consistency call.
- [ ] 6.3 Red: `CupResolutionTest` takes a snapshot; it fails to compile until the signature changes.
- [ ] 6.4 Green: `CupResolution.resolve` takes `CatalogSnapshot`; use `cupNames()` and `cupByName` in place of the `JsonCupCatalog` methods.
- [ ] 6.5 Refactor: update `CommittedMapDataTest` to load through `CatalogLoader`; update the `ServerBeans` and `ServerSettings` javadoc that names `JsonMapCatalog` or `CatalogConsistency`.

## 7. Verify architecture rules (fitness)

- [ ] 7.1 Run `./gradlew :voyager-fitness:test`; expect green with no rule change. If a rule fails, stop and report the rule and the class; do not add or weaken a rule without approval.
- [ ] 7.2 Confirm `CatalogLoader`, `CatalogSnapshot` and `InvalidCatalogException` carry no DI annotation (ArchUnit or `grep` over `voyager-platform`).

## 8. Documentation

- [ ] 8.1 Commit `docs(architecture)`: update the `catalog` row of the package table in `docs/explanation/architecture.md` (created by the sibling change; if absent, leave the row for that change and note it) and set research 005 roadmap Q1 to done, citing this change.

## 9. Verify and open the pull request

- [ ] 9.1 Run `./gradlew build` over both trees and `openspec validate unify-catalog-loading --strict`; both must pass before the PR opens.
- [ ] 9.2 Open the pull request with title `refactor(platform): load map and cup catalogues through one entry point`, body linking this change, and the referral footer required by project memory.
