## Why

`RaceRuns` sits in `platform.world`, which is infrastructure, and it imports `race.flow.RaceClock` and `race.run.RaceRun`.
The frozen rule R3 (`r3_platformInfrastructureDoesNotDependOnRace`) holds that edge as migration item 11 in its baseline.
The next change, `add-out-of-bounds-reset`, must write replaced runs into `RaceRuns`, which would add frozen violations to
the baseline. The run holder is cup state, and it has one caller, `MapTransition`, which is cup behaviour too (item 12).
Both move into the cup slice now, so the baseline shrinks and the next change can write runs without growing it.

## What Changes

- Move `net.elytrarace.voyager.platform.world.RaceRuns` to `net.elytrarace.voyager.platform.cup.RaceRuns`, unchanged in
  behaviour. Its exception `UnstartedRunException` moves to `platform.cup.exception`, with a new `package-info.java`.
- Move `net.elytrarace.voyager.platform.world.MapTransition` to `platform.cup.MapTransition` (migration item 12). It is the
  only caller of the package-private lifecycle methods `RaceRuns.startFresh` and `RaceRuns.clearAll`, so both classes
  move together, and `platform.world` keeps no dependency on the cup.
- Move `RaceRunsTest` and `MapTransitionTest` with them. Their assertions do not change.
- Update imports only in `CupSession`, `WaitingRoomTest` and the `voyager-server` wiring and tests that name the two classes.
  The golden master harness changes imports only, and its golden files do not change.
- Shrink the frozen R3 store by the seven `RaceRuns` lines, and add `RaceRuns.java` to the closed-item list of
  `ClosedViolationsAreGoneTest`.
- Close migration items 11 and 12 in `docs/explanation/architecture.md`, and record the placement in ADR-0023 (Proposed).

No behaviour changes. No public API changes. Nothing outside `platform.cup`, `platform.world`, `voyager-server` wiring,
`voyager-fitness` and `docs` changes.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `architecture/vertical-slices`: adds the requirement that the race-run holder and the map transition live in the cup slice
  of `voyager-platform`, not in the infrastructure package `platform.world`.

## Commit type and scope

Ships as `refactor(platform)`. The fitness guard (`test(fitness)`) and the migration-list update (`docs(architecture)`) are
parts of the same fix, required by `docs/guides/how-to-refresh-the-architecture-baseline.md`, and they are squashed into one
pull request whose title is `refactor(platform): move the race-run holder out of the world package`.

## Slices and scope

- **Touched slices:** `cup` (platform side) only. `run` (the design table's earlier target `platform.run`) is not created; see
  `design.md`, decision 1.
- **Out of scope:** migration items 13 (`XerusPhaseDriver`, `RacePhaseListener`), 14 (`FlightTick`, `FlightTickDriver`),
  15 (`GuideLineRenderer`), 16 (`MinestomCollisionSpace`), 27 (the `catalog` and `world` cycle), and every item of follow-ups
  4 and 5. `add-out-of-bounds-reset` and `add-alpha-cup-content` are not touched.
- **Decision affected:** none of the greenfield design spec's D-numbers. The placement table in `docs/explanation/architecture.md`
  changes for the `run` and `cup` rows, and ADR-0023 records why.

## Impact

- `voyager-platform`: two classes and one exception move from `world` to `cup`; two test classes move; `CupSession` and
  `WaitingRoomTest` drop imports; `world/package-info.java` loses the paragraph that describes the run holder.
- `voyager-server`: imports in `CupBeans`, `ServerBeans`, `CupSessionGoldenMasterTest`, `VoyagerGraphTest`,
  `RaceCommandReloadTest`, `CupSessionLobbyTest`, `CupSessionRoundPinTest`, `CupSessionTest` and `CupWiring`.
- `voyager-fitness`: `archunit_store/` loses the seven `RaceRuns` lines of R3; `ClosedViolationsAreGoneTest` gains one entry.
- `docs`: `explanation/architecture.md` (slice table, enforcement table, migration status) and `decisions/0023` (new, Proposed).
