# Proposal

**Conventional Commits:** `refactor(platform)`. Squash-merge PR title: `refactor(platform): load map and cup catalogues through one entry point`.

Slice: `catalog` (ring 3, `voyager-platform`), with its single consumer in `voyager-server` (`inject/ServerBeans`, `game/CupResolution`). Out of scope: the setup slice, hot reload (Q7), scoped cup validation (Q4), the validate task and the all-at-once report (Q6, `add-catalog-validate-task`), schema versioning (Q3), ring index changes (Q2), and any change to the tree being replaced. Decision D10 is not altered.

## Why

Maps and cups are read by two parallel classes, `JsonMapCatalog` and `JsonCupCatalog`, each wired by its own `@Bean`. The cross-check that a cup's maps exist runs in a third place, the `cup` bean factory method in `ServerBeans`. Each new feature that touches catalogue data (scoped validation, the validate-and-exit task, hot reload from research 005 Q4, Q6 and Q7) would otherwise have to thread through three entry points and re-decide when a directory is read. Research 005 (roadmap Q1) asks for one load point. The owner decided on 2026-10-09 that this change stays behaviour-preserving: boot keeps failing on the first problem with today's exception and message. Collecting every problem for an operator report is a separate concern and belongs to `add-catalog-validate-task`.

## What Changes

- Add one load point in `voyager-platform` `catalog`, `CatalogLoader`, that reads `maps/` and `cups/` from a data directory.
- `CatalogLoader.read(Path)` returns a `CatalogReading`: an immutable `CatalogSnapshot` of the definitions that parsed, and the list of every `CatalogProblem` found, as data, in a deterministic order. Nothing is thrown for a bad file. Other changes (scoped cup validation, the validate task, hot reload) consume this list.
- `CatalogLoader.load(Path)` returns the `CatalogSnapshot`, or throws the cause of the **first** problem in today's order: `cups/` before `maps/`, then the cross-catalogue check. That is the order boot fails in today (avaje builds `JsonCupCatalog` before `JsonMapCatalog`), not the order the source lines suggest. The thrown exception is today's exception object with today's message.
- The cup-to-map consistency check becomes part of the load, not a separate step the composition root must remember to call.
- **BREAKING (within the rebuild only):** `JsonMapCatalog` and `JsonCupCatalog` are removed. Their callers in `voyager-server` take the snapshot or the `MapCatalog` / `CupCatalog` ports. No file format changes.
- **Behaviour preserved:** a valid data directory produces the same map and cup definitions and the same cup selection as today; an invalid one refuses boot with the same exception type, the same message and the same first problem as today.
- **Not in this change:** the aggregate report that lists every problem at once, and the `InvalidCatalogException` that would carry it. That is `add-catalog-validate-task`, which owns it and may use it on the boot path.

## Capabilities

### New Capabilities
- `catalog/catalog-loading`: reading map and cup definitions from one data directory into an immutable snapshot, exposing every problem as data, and refusing boot on the first problem with today's exception.

### Modified Capabilities
- None. `openspec/specs/` holds no catalogue requirement today.

## Impact

- **Code (platform):** new `CatalogSnapshot`, `CatalogReading`, `CatalogProblem` and `CatalogLoader` in `net.elytrarace.voyager.platform.catalog`; `CatalogDirectory` and `CatalogConsistency` are reused or folded in; `JsonMapCatalog` and `JsonCupCatalog` deleted with their tests migrated.
- **Code (server):** `ServerBeans` gains one `CatalogSnapshot` bean and the two port beans; `CupResolution` takes the snapshot; `CommittedMapDataTest`, `CupResolutionTest` and `VoyagerGraphTest` follow the new types.
- **Fitness:** no new rule is required; the existing `FitnessCoverageTest` and the DI-confinement rules already cover `voyager-platform` and `voyager-server`.
- **Docs:** `docs/explanation/architecture.md` and research 005 roadmap Q1 status, updated in the same PR as the code.
- **Runtime:** none. Boot behaviour and boot-time messages are unchanged.
