# Proposal

**Conventional Commits:** `feat(server)`. Squash-merge PR title: `feat(server): pick up changed maps and cups between rounds without a restart`.

Roadmap reference: `docs/research/005-simpler-map-and-cup-setup.md`, candidate Q7, EARS-B6. The roadmap row says
`feat(platform)`; this proposal uses `feat(server)` as the project owner asked, because the round boundary, the swap and the
operator command live in the server composition. The reloader is one platform class. Both parts are the same `feat` type, so the
change does not split.

**Depends on:** `unify-catalog-loading` (roadmap Q1, "Single load point for catalogs"; proposal in flight). Hot reload reuses its
`CatalogLoader`, its `CatalogSnapshot` and its aggregate problem report. A second loader would be the drift Q1 removes.
Related and not a hard dependency: `scope-cup-validation` (roadmap Q4). Until it lands, one broken unrelated cup in `cups/` blocks
every reload. That is safe, because the last good catalogue is kept (design, decision 5).

## Why

Every edit to a map or cup file needs a server restart, and the catalogues are read once at boot (research 005, P5, E1). A race
server that restarts for each ring fix loses its players and the cup in progress. The change lets an operator apply a valid edit
between rounds, and keeps the last good catalogue when an edit is broken, so a typo never takes the running server down.

In this proposal a **round** is one cup run: from `CupSession.start` until the cup finishes or is stopped. A reload takes effect at
the start of the next round.

## What Changes

- Add `/race reload` (operator subcommand, requires permission `voyager.race.reload`). It re-reads `maps/` and `cups/` from the
  configured data path, validates the whole set, and reports the outcome to the sender.
- Add `CatalogReloader` (voyager-platform). It runs `CatalogLoader.load` from `unify-catalog-loading`, resolves the selected cup,
  checks the worlds of new maps, and returns `ReloadOutcome`: `Applied` with the new `LoadedCatalog`, or `Rejected` with every problem.
  It never throws for a bad edit.
- Add `CatalogHolder` (voyager-platform). It holds the current `LoadedCatalog` and at most one pending one. A pending catalogue becomes
  current only when a round starts. Readers never see a partial catalogue.
- `CupSession` pins the catalogue a round starts with. A reload never changes the rotation, a map, or a ring of a round in progress.
- Add `WorldOpener` (platform port, implemented by `MapInstances`) and `MapInstances.discard(world)`, so a rejected reload leaves no
  newly opened world behind.
- **BREAKING** (internal only): the `cup`, `MapCatalog` and `CupCatalog` beans are removed from `ServerBeans`, and the
  `CatalogHolder` bean replaces the boot snapshot as what the graph holds. No external API changes.
- Record the decision in an ADR (MADR 4.0), `docs/decisions/0017-pin-catalogue-snapshot-per-round.md` (0016 is reserved by
  `switch-di-to-avaje-inject`; confirm the number before merge).
- Add a how-to guide for operators (Diataxis how-to), and update research 005 for Q7.

## Capabilities

### New Capabilities
- `server/catalog-reload`: how a running server applies changed map and cup definitions. It covers the operator trigger, the round
  boundary as the only apply point, all-or-nothing validation, the keep-last-good failure rule, and which world changes are reloadable.
  It is a new capability because no spec under `openspec/specs/` covers catalogue lifecycle, and its rules are contracts the round and
  startup code must keep.

### Modified Capabilities
- None. `openspec/specs/` is empty today, and this change adds no requirement to another capability. The catalogue loading requirements
  of `unify-catalog-loading` are used, not changed.

## Out of Scope

- A file watcher (WatchService or polling). Deferred, see design, decision 3. The admin command is the only trigger.
- Reloading a map at a map boundary inside a running cup. Rejected for this change, see design, decision 1.
- Re-reading or reopening the region data of a world that is already open. Needs a restart.
- Unloading worlds that a removed map used to reference.
- Versioned snapshots, rollback, and world copies (research 005, open question 6). Owner decision.
- Publishing rights and the setup slice (`voyager-setup`, roadmap Phase 1, E6), and the validate task (`add-catalog-validate-task`, Q6),
  which reads the same loader but is not needed for reload.
- The tree being replaced (`server`, `plugins/*`, `shared/*`). It gets no reload.
- Game rules, scoring and the ring model. Rings stay discs (research 005, owner decisions 2026-10-09).

## Impact

- **voyager-platform**: `LoadedCatalog`, `CatalogHolder`, `ReloadOutcome`, `CatalogReloader`, `WorldOpener`; `MapInstances` implements
  `WorldOpener` and gains `discard(world)`. Uses `CatalogLoader`, `CatalogSnapshot` and `InvalidCatalogException` from `unify-catalog-loading`.
- **voyager-server**: `ServerBeans` (drops the port and `cup` beans, adds holder, reloader and service beans), `RaceBeans` and `CupSession`
  (the holder replaces `CupDefinition` and `MapCatalog`), `VoyagerServer` (rotation and worlds from the holder), `RaceCommand` (the
  `reload` literal), and a new `CatalogReloadService` that runs reloads off the tick thread.
- **voyager-api, voyager-race**: no change.
- **voyager-fitness**: no new rule. `ApiPurityTest` and `FitnessCoverageTest` already cover the DI boundary.
- **Dependencies**: none new. Virtual threads and `AtomicReference` are JDK 25.
- **Operations**: the reload needs the `voyager.race.reload` permission. Minestom ships no permission layer, so who holds it depends on the
  permission source the server runs with (the console is always allowed). The spike (tasks group 1) confirms the check API; the how-to
  states the default.
- **Players**: no visible change, except that the next round may use changed maps.
