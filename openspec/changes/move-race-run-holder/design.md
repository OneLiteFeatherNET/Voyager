## Context

See `proposal.md` for the motivation. The baseline entry is migration item 11 (`RaceRuns` imports `race.flow` and `race.run`),
and item 12 is `MapTransition`, which is cup behaviour in the world package. The archived `define-clean-architecture-with-vertical-slices`
design (section 3, the package table) names two targets: `platform.world.RaceRuns` to `platform.run`, and `MapTransition` to
`platform.cup`.

Facts that shape the choice, checked against `main` at `1819d6f`:

- `RaceRuns` writes runs only through `startFresh` and `clearAll`, both package-private, and both are called only by
  `MapTransition`. The class javadoc relies on that: a run cannot exist for a racer who is not standing on the map it is over.
- `MapTransition` calls nothing in `race`. Its only race-side link is the `RaceRuns` it is given.
- `platform.cup` is imported by one class outside itself: `platform.lobby.WaitingRoom`. The cup already imports
  `platform.world` (`MapInstances`, `WorldHealth`, `CurrentMapBlocks`, `MapTransition`, `RaceRuns`), so `cup -> world` exists
  today and no edge runs back from `world` to `cup`.
- `platform.run` does not exist. It would be a one-class slice.

## Goals / Non-Goals

**Goals:**
- No class in `platform.world` depends on `race..` or on the cup.
- The R3 store loses the `RaceRuns` lines and gains none.
- Behaviour, golden files and the public API of `RaceRuns` and `MapTransition` stay as they are.

**Non-Goals:**
- Splitting `platform.run` out of `cup`, or moving `XerusPhaseDriver`, `FlightTick`, `GuideLineRenderer` or
  `MinestomCollisionSpace` (items 13 to 16).
- Breaking the `catalog`/`world` cycle (item 27).
- Adding a new fitness rule. The placement is covered as described under Risks.

## Decisions

### 1. The holder goes to `platform.cup`, not to `platform.run`

`RaceRuns` moves to `platform.cup`, the package where `MapTransition` also goes.

- **Why:** the lifecycle methods are package-private, and they stay so only if their caller is in the same package. Moving
  `RaceRuns` alone to `platform.run` would force `startFresh` and `clearAll` to `public`, and any class in the platform could
  then start a run for a racer who is still on the previous map. The cup is also the owner of the state: the cup advancing
  is what clears it. The spec `vertical-slices` already says that the cup's Minestom-side state lives in `platform.cup`.
- **Alternative A, `platform.run` with public lifecycle methods:** rejected for the reason above.
- **Alternative B, `RaceRuns` in `platform.cup` but `MapTransition` in `platform.world`, joined by a port declared in
  `world`:** the port's methods would have to be `public` on `RaceRuns` (the same loss as A), and the constructor of
  `MapTransition` would change, so the golden master harness would change beyond its imports.
- **Alternative C, `CupSession` does the run bookkeeping and `MapTransition` takes a per-arrival callback:** rejected. The
  constructor of `MapTransition` changes, and the order of "drop runs, then move, then give each arrived racer a run" moves
  out of the class that documents it. The failure path (a world that does not exist leaves every racer's run in place) has
  to be re-proved in the cup.

### 2. `MapTransition` moves with `RaceRuns` (item 12)

Keeping `MapTransition` in `platform.world` would make `world` depend on `cup`, which is the same kind of edge R3 forbids,
and it would require one of the alternatives above. Moving it changes no code: its only change is the package and the imports.
Item 12 closes with it.

### 3. Exception placement

`UnstartedRunException` is thrown only by `RaceRuns`. It moves to `platform.cup.exception`, with a `package-info.java`
annotated `@NotNullByDefault`, as `DesignRuleTest` and `NullabilityConventionTest` require.

### Dependency direction after the move

```
platform.lobby  ----> platform.cup
platform.cup    ----> platform.world (MapInstances, WorldHealth, CurrentMapBlocks)
platform.cup    ----> platform.catalog, flight, hud, render, text, tick, convert
platform.cup    ----> race.run, race.flow, race.cup, race.scoring, api.*
platform.world  ----> platform.catalog (WorldOpener), platform.convert, api.*      (no race, no cup)
```

The removed edges are `world -> race` (`RaceRuns`) and `world -> world.exception.UnstartedRunException`. No edge into `platform.cup`
is added except from `lobby`, which exists today.

### Fitness coverage

- R3 (`r3_platformInfrastructureDoesNotDependOnRace`, frozen): the `RaceRuns` lines leave the store on the local run, and no
  line is added. This is the item-11 closure.
- R4 (`r4_platformSlicesAreFreeOfCycles`, frozen): no new cycle, because `world` gains no edge to `cup`. The store keeps its
  single `catalog`/`world` cycle (item 27).
- `ClosedViolationsAreGoneTest`: `RaceRuns.java` joins the closed-item list, so a stored line naming it fails at once.
- `FitnessCoverageTest`: unchanged, since no module is added.

### Store refresh

The store is refreshed the way `docs/guides/how-to-refresh-the-architecture-baseline.md` describes: a local
`./gradlew :voyager-fitness:test` without `CI`, and a check that the diff shows removed lines only.

### Architecture decision record

The placement is an architecture decision, so it gets ADR-0023 in `docs/decisions/`, with status Proposed. The owner accepts it.

## Risks / Trade-offs

- **[The placement is not enforced by a rule]** Nothing stops a later change from making `platform.world` depend on `platform.cup`,
  because that edge is outside R3's scope. -> Mitigation: this is a named gap in `docs/explanation/architecture.md`, and the
  closed-item guard stops the stored `RaceRuns` violations from returning. A plain rule (`platform.world` does not depend on
  `platform.cup`) would pass today; the owner may ask for it as a follow-up.
- **[The other open change assumes `MapTransition` stays in `platform.world`]** `add-out-of-bounds-reset` (uncommitted, not
  touched here) names `platform.world` for `MapTransition.reposition` and says the prerequisite moves run bookkeeping into
  `CupSession`. -> Mitigation: the report to the owner names this conflict. That change must target `platform.cup.MapTransition`.
  Its R3 concern is still met, because `platform.cup` is not an R3 package.
- **[Behaviour drift]** Moving classes can change visibility-dependent code. -> Mitigation: the package-private methods are called
  only by the moved `MapTransition`; the full `voyager-platform` and `voyager-server` test suites and the four golden scenarios run.

## Migration Plan

1. Commit the OpenSpec change (`docs(openspec): propose move-race-run-holder`).
2. Red: append `RaceRuns.java` to the closed-item list locally, and see the guard fail on the seven stored lines.
3. Move the classes and their tests, update imports, and refresh the store locally. Commit as `refactor(platform)` with the
   shrunk store.
4. Commit the guard entry as `test(fitness)`.
5. Commit the migration list and ADR-0023 as `docs(architecture)`.
6. Verify, merge with `--no-ff` into `main`, archive, and record the PR title.

Rollback: `git revert` of the merge commit restores `RaceRuns` and `MapTransition` in `platform.world` and restores the
store. The store lines return only by the same local refresh, so the revert restores the file content as well.

## Open Questions

None. The placement is decided above and recorded in ADR-0023.
