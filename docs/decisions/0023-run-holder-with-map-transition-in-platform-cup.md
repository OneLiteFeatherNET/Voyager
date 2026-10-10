# ADR-0023: The race-run holder and the map transition live in platform.cup

## Status

Proposed

The project owner has not accepted this record yet. It becomes Accepted only when the owner accepts it. The change that
implements it is `move-race-run-holder`, which does not mark this record Accepted.

## Date

2026-10-10

## Decision makers

- TheMeinerLP (project owner). To be decided: the placement of `RaceRuns` and `MapTransition` in `platform.cup`, and the
  choice not to create a `platform.run` package.
- Drafted with the change `move-race-run-holder`.

## Context and problem statement

`RaceRuns` held one `RaceRun` per racer in `platform.world`, an infrastructure package, and imported `race.flow` and
`race.run`. The frozen rule R3 recorded that edge as migration item 11. `MapTransition` also sat in `platform.world`, and
it is the only caller of `RaceRuns`' lifecycle methods, which are package-private so that a run exists only for a racer
standing on the map it is over. The archived design of `define-clean-architecture-with-vertical-slices` named two targets
for them: `platform.run` for `RaceRuns`, and `platform.cup` for `MapTransition`.

The next change, `add-out-of-bounds-reset`, has to write runs into `RaceRuns`. It must not add a frozen violation.

## Decision drivers

- `platform.world` must not depend on `race..`, and it should not depend on the cup either.
- The package-private guarantee of `RaceRuns` (a run starts only at a map's spawn, and is dropped before racers move) must
  keep holding in code, not only in a comment.
- The golden master harness changes imports only.
- The `vertical-slices` specification says that the cup's Minestom-side state lives in `platform.cup`.

## Considered options

1. **`RaceRuns` in `platform.run`, `MapTransition` in `platform.cup`, as the design table says.** Rejected: the two classes
   are in different packages, so `startFresh` and `clearAll` would have to become `public`, and any platform class could
   then start a run for a racer who has not arrived.
2. **Both in `platform.cup` (chosen).** The lifecycle methods stay package-private, and the cup owns the state it clears.
   `platform.world` depends on neither. `platform.run` is not created, because it would hold one class.
3. **`RaceRuns` in `platform.cup`, `MapTransition` in `platform.world`, joined by a port declared in `world`.** Rejected:
   the port's methods must be public on `RaceRuns`, which is the loss of option 1, and `MapTransition`'s constructor changes,
   so the golden master harness changes beyond its imports.
4. **`CupSession` does the run bookkeeping, and `MapTransition` takes a per-arrival callback.** Rejected: `MapTransition`'s
   constructor changes, and the order of "resolve the world, drop the runs, move each racer, then give each arrived racer a
   run" moves out of the class that documents it.

## Decision outcome

Chosen option: **2**. `RaceRuns` and `UnstartedRunException` (now in `platform.cup.exception`) and `MapTransition` move to
`platform.cup`. Their tests move with them. Item 11 and item 12 of the migration list are closed.

## Consequences

- Good: the R3 frozen store loses the seven `RaceRuns` lines and gains none. `platform.world` holds no race or cup type.
- Good: the lifecycle methods of `RaceRuns` stay package-private, and `MapTransition` keeps calling them.
- Good: the golden master harness changes imports only, and its golden files do not change.
- Bad: no rule forbids `platform.world` from depending on `platform.cup`. The placement is enforced by review, and
  `ClosedViolationsAreGoneTest` covers the stored lines only. A plain rule could close this gap later.
- Bad: the change `add-out-of-bounds-reset` (uncommitted) expects `MapTransition` to stay in `platform.world`. It has to
  name `platform.cup.MapTransition` instead.

## More information

- `openspec/changes/archive/2026-10-10-define-clean-architecture-with-vertical-slices/design.md`, section 3 (the original
  targets) and the migration list, items 11 and 12.
- `openspec/changes/move-race-run-holder/design.md`, decision 1.
- [ADR-0020](0020-freeze-architecture-violations-as-baseline.md): the frozen baseline that item 11 was recorded in.
- [ADR-0021](0021-cup-use-cases-in-race-cup.md): the cup's placement in `race.cup` and `platform.cup`.
