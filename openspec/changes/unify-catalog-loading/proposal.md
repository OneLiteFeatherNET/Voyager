# Proposal

**Conventional Commits:** `refactor(platform)`. Squash-merge PR title: `refactor(platform): load map and cup catalogues through one entry point`.

Slice: `catalog` (ring 3, `voyager-platform`), with its single consumer in `voyager-server` (`inject/ServerBeans`, `game/CupResolution`). Out of scope: the setup slice, hot reload (Q7), scoped cup validation (Q4), the validate task (Q6), schema versioning (Q3), ring index changes (Q2), and any change to the tree being replaced. Decision D10 is not altered.

## Why

Maps and cups are read by two parallel classes, `JsonMapCatalog` and `JsonCupCatalog`, each wired by its own `@Bean`. The cross-check that a cup's maps exist runs in a third place, the `cup` bean factory method in `ServerBeans`. Each new feature that touches catalogue data (scoped validation, the validate-and-exit task, hot reload from research 005 Q4, Q6 and Q7) would otherwise have to thread through three entry points and re-decide when a directory is read. Research 005 (roadmap Q1) asks for one load point. Because the load also fails at the first bad file, an operator still learns of one problem per restart, which NFR-007 forbids; that gap is part of the same load path and is closed here rather than in a later change.

## What Changes

- Add one load point in `voyager-platform` `catalog` that reads `maps/` and `cups/` from a data directory and returns an immutable `CatalogSnapshot` value holding both definitions by name.
- The load collects every problem it finds (missing or empty directories, unparsable files, duplicate names, and cups naming unknown maps) into one report and refuses to produce a snapshot if any exist. Today the first problem aborts the load.
- The cup-to-map consistency check becomes part of the load, not a separate step the composition root must remember to call.
- **BREAKING (within the rebuild only):** `JsonMapCatalog` and `JsonCupCatalog` are removed. Their callers in `voyager-server` take the snapshot or the `MapCatalog` / `CupCatalog` ports. No file format changes.
- **Behaviour preserved:** a valid data directory produces the same map and cup definitions and the same cup selection as today; boot still refuses on any problem, with the same exception family (`UnreadableCatalogException`, `MalformedCatalogFileException`, `DuplicateCatalogEntryException`, `UnresolvedCupMapException`) as the cause of the aggregate report.
- **Behaviour extended:** the refusal message lists every problem, not only the first. This is the one intentional observable change and is covered by a scenario.

## Capabilities

### New Capabilities
- `catalog/catalog-loading`: reading map and cup definitions from one data directory into an immutable snapshot, collecting all problems, and refusing an inconsistent catalogue as one unit.

### Modified Capabilities
- None. `openspec/specs/` holds no catalogue requirement today.

## Impact

- **Code (platform):** new `CatalogSnapshot` and a loader in `net.elytrarace.voyager.platform.catalog`; `CatalogDirectory` and `CatalogConsistency` are reused or folded in; `JsonMapCatalog` and `JsonCupCatalog` deleted with their tests migrated.
- **Code (server):** `ServerBeans` gains one `CatalogSnapshot` bean and the two port beans; `CupResolution` takes the snapshot; `CommittedMapDataTest`, `CupResolutionTest` and `VoyagerGraphTest` follow the new types.
- **Fitness:** no new rule is required; the existing `FitnessCoverageTest` and the DI-confinement rules already cover `voyager-platform` and `voyager-server`.
- **Docs:** `docs/explanation/architecture.md` and research 005 roadmap Q1 status, updated in the same PR as the code.
- **Runtime:** none on a valid catalogue; boot-time error text changes for an invalid one.
