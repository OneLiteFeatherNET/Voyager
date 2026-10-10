# Architecture: rings and vertical slices

This page explains how the rebuild (`voyager-*` modules) is organised and why. It is an explanation, not a how-to:
it tells you where code belongs and what the rules protect. The decision record is
[ADR-0017](../decisions/0017-clean-architecture-with-vertical-slices.md). The normative statements are the
requirements of the archived OpenSpec change
`openspec/changes/archive/2026-10-10-define-clean-architecture-with-vertical-slices/specs/`. The migration list of code that does not follow the rules yet is in that change's
`design.md`, and this page links to it rather than copying it.

The rebuild uses two organising principles at once:

- **Clean Architecture** decides the modules and the direction of dependencies. A module is a *ring*.
- **Vertical Slice Architecture (VSA)** decides the packages inside each module. A feature is a *slice* that runs
  through the rings and owns its types from end to end.

A class is placed by two questions: which ring is it in (its module), and which slice does it serve (its package).

## The rings

```
  ring 4  composition roots     voyager-server          voyager-setup
            |                   wire, sequence and start the slices; compute no score, standing or order
            v
  ring 3  adapters and          voyager-platform        voyager-persistence (later)
          frameworks            Minestom, Xerus, Gson, Falco; implements ports; converts values
            |
            v
  ring 2  use cases             voyager-race            race, flow, scoring, cup, ring, line, run
            |
            v
  ring 1  entities              voyager-physics ----> voyager-api
          (pure)                flight simulation      values, records, ports, exceptions
```

Dependencies point inward, toward `voyager-api`. Gradle enforces the edges at compile time, because each module lists
its allowed dependencies in its build file. The allowed edges are:

| Module | May depend on |
|---|---|
| `voyager-api` | the JDK and annotations only |
| `voyager-physics` | `voyager-api` |
| `voyager-race` | `voyager-api`, `voyager-physics` |
| `voyager-platform` | `voyager-api`, `voyager-physics`, `voyager-race` |
| `voyager-persistence` (later) | `voyager-api` |
| `voyager-setup` | `voyager-api`, `voyager-platform` |
| `voyager-server` | every other rebuild module |

`voyager-fitness` is test-only and sits outside the rings.

### Why rings are modules

Packages would leave the direction to review. A module makes a wrong import a compile error, and it confines Minestom
to the platform module by design. Today the composition root of the game server (`VoyagerServer`, `server.inject`) also imports Minestom, which its
bootstrap needs and the architecture allows, and the setup module's adapter code does too, which is an open item in the
migration list. The alternatives were one module with packages per ring, which needs ArchUnit to
hold every edge, and one module per slice, which multiplies the Gradle graph and would split each Minestom adapter over
several modules.

Ring 3 and ring 4 are separate rings, but the composition root of the setup server (`voyager-setup`) also holds
Minestom adapter code today. That is recorded as an open migration item in the change's `design.md`, and it is the one
place where the module layout and the ring model disagree.

### The dependency rule, concretely

- An entity (`voyager-api`, `voyager-physics`) imports no framework, no file I/O and no DI annotation.
- A use case (`voyager-race`) imports no framework and no DI annotation. A whole race runs in a plain JUnit test.
- An adapter (`voyager-platform`) implements the ports that the inner rings declare. It converts between platform
  values and domain values, and it translates external exceptions into its own ring's exceptions before they reach an
  inner ring.
- A composition root wires everything, and it holds no game logic.

Ports are declared in the innermost module that calls them, or in `voyager-api` when more than one module calls them.
The collision port `CollisionSpace` is an example: physics calls it, platform implements it, and it lives in
`voyager-api`. Data crosses a boundary as an immutable record or enum that carries no platform type. Domain records
validate in their compact constructor, so `Vec3` never holds a NaN or infinite component.

## The slices

A slice is a feature. It owns its use-case types, its ports and its adapters, and each slice's packages sit under
`net.elytrarace.voyager.<module>.<slice>..`. A module with one slice uses its root package as that slice: in
`voyager-physics` the root package is the flight slice, and `collision`, `step` and `math` are technical layers inside it.

The slices and their packages today:

| Slice | `voyager-api` | `voyager-physics` | `voyager-race` | `voyager-platform` | `voyager-server` / `voyager-setup` |
|---|---|---|---|---|---|
| `flight` | `api.physics` | `physics` (root, `collision`, `step`, `math`) | - | `platform.flight` (Racers, Rockets); `platform.cup` (LivePlayerSampler); `platform.collision` and `platform.tick` hold flight classes that move | - |
| `ring` | `api.race` (Ring, RingType), `api.race.effect` | - | `race.collision`, `race.progress`, `race.effect`; target `race.ring` | - | - |
| `run` | - | - | `race.run` (RaceRun) | `platform.cup.RaceRuns`, the holder of one run per racer, which stays with the cup (decided by `move-race-run-holder`, see ADR-0023) | - |
| `flow` | `api.race.GameMode` (kernel) | - | `race.flow` (RaceStateMachine, RaceClock, RaceTimings) | `platform.tick` XerusPhaseDriver and RacePhaseListener move to `platform.flow` | `server.config` reads RaceTimings |
| `scoring` | `api.race` (MedalTier, MedalBrackets); target `api.scoring` | - | `race.scoring` (MapScorer, CupScorer, Placement, MedalOutlook) | consumers only: `platform.text.Messages`, `platform.hud.HudState` | - |
| `cup` | `api.race` (CupDefinition, CupCatalog); target `api.cup` | - | `race.cup`: `CupRound` (use-case facade), `CupStandings`, `CupStanding`, `MapFigures`; `exception.UnresolvedCupException` | `platform.cup`: `CupSession` (the Minestom-facing cup), `CupAnnouncer`, `TickStep` and `TickPipeline`, `LivePlayerSampler`, `RaceRuns` and `MapTransition` (both moved from `platform.world` by `move-race-run-holder`); `platform.hud.HudStates` maps figures to HUD state | `VoyagerServer` and `CupBeans` wire it; no cup logic |
| `line` | `api.race` (GuideLine, GuidePoint); target `api.line` | - | `race.line` (RacingLine, CatmullRom) | `platform.render.GuideLineRenderer` moves to `platform.line` | - |
| `catalog` | `api.race` (MapCatalog, MapDefinition); target `api.catalog` | - | - | `platform.catalog`: one entry point, `CatalogLoader.load` and `read`, which returns an immutable `CatalogSnapshot`; `CatalogReloader` and `CatalogHolder` hold the played catalogue for hot reload; `adapter`, `writer`, `exception`. `JsonMapCatalog` and `JsonCupCatalog` are removed (unify-catalog-loading) | `server.config.ConfigCheck` (composition check) |
| `hud` | - | - | - | `platform.hud` (HudState, HudStates, RaceHud, RaceFeedback, StartCountdown) | - |
| `reset` | - | - | `race.reset` (RunReset, ResetPlan, ResetCause; `exception.RunNotResettableException`): the rules of leaving the bounds and landing, pure | `platform.cup.RunResetter` performs a reset: the run, the burn, the shadow flight, the move and the relaunch (`Racers.launch`, `FireworkBoostTracker.cancelBurn`, `MapTransition.reposition`), and the feedback | `CupSession.raceTick` decides; no wiring of its own |
| `lobby` | - | - | `race.flow.StartGate` (the start decision table, pure) | `platform.lobby`: `WaitingRoom` (the Minestom side of the gate, ticked after the cup's own tick, not a pipeline step) | `server.inject.ServerBeans.waitingRoom` wires it; `server.command.RaceCommand` shows its status line |
| `mapsetup` | `api.mapsetup` (DraftStore, MapDraft, MapId) | - | - | `platform.catalog` holds its JSON store today | `setup.mapsetup` (pure); `setup.adapter` (Minestom, see the migration list) |
| kernel | `api.math` (Vec3, Aabb); `api.race.GameMode` | - | - | - | - |
| infrastructure | `api.config` (ConfigProblem) | - | `race` root (RaceCore) | `platform` root, `platform.text`, `platform.convert`, `platform.world` (instances, world health, CurrentMapBlocks), `platform.tick` (base) | `server` (VoyagerServer, CatalogReloadService), `server.command`, `server.config`, `server.inject`; `setup` root, `setup.config`, `setup.inject` |
| future: `record` | - | - | - | - | `voyager-persistence` (E5), not yet in the build |

The table reads the current code. Where a package still holds a class of another slice, the cell names the target
package. The change's `design.md` holds the same table with file-level evidence and the migration list.

### Slices and their names

The owner decided on 2026-10-09 that the cup flow moves to a new `race.cup` slice and that `progress` folds into `ring`.
Three naming questions were still open. They were decided by default on 2026-10-10, and the owner may revise them:
`catalog` and `hud` stay as slice names, and the package `api.physics` keeps its name for now. Its rename to `api.flight`
belongs to the `flatten-api-by-slice` follow-up.

### Technical packages are infrastructure

A package named for a feature is a slice. A package named for a technical role is infrastructure. Infrastructure may
serve several slices, but it owns no use-case type. The infrastructure packages are `text` (message handling), `convert`
(domain-to-platform value conversion), `world` (instances and world health), `tick` (the tick driver base), `config`,
`command` and `inject`. A class whose behaviour belongs to one feature moves into that feature's package, even when it
sits in an infrastructure package today: `platform.render` has one class, and it renders the racing line, so it belongs
to `line`.

## Cross-slice rules

- **Calls go through public types.** A slice uses another slice's root package and its `exception` package. A helper that
  must stay hidden lives in `<slice>.internal`, and no class outside the slice imports it.
- **The graph is acyclic.** Inside `race`, the edges run from `run` to `flow`, `progress` (part of `ring`) and `scoring`,
  and from `scoring` to `progress`. `ring` depends on nothing else in `race`. Folding `progress` into `ring` keeps this
  graph acyclic. A separate `progress` package would close a cycle through `run` and `scoring`.
- **The shared kernel is small.** It holds `api.math` (vectors and boxes) and the `GameMode` discriminator. Kernel types
  are immutable and platform-free. A change to the kernel needs an ADR, because every slice sees it.
- **Slices talk through the composition root.** Inside a tick, the composition root sequences the steps. There is no
  shared mutable state between slices.

## Exceptions

Each domain exception sits in an `exception` subpackage next to the code that raises it, has a `package-info.java`,
extends `RuntimeException`, and ends in `Exception`. An adapter translates I/O, Minestom, Gson or Falco failures into its
own ring's exceptions. An inner ring never catches an exception of an outer ring. The
`exception` packages of the rings follow this shape:

| Ring | Example |
|---|---|
| 1 entities | `api.math.exception.NonFiniteVectorException`, `physics.exception.InvalidSimulationStateException` |
| 2 use cases | `race.flow.exception.IllegalPhaseTransitionException` |
| 3 adapters | `platform.catalog.exception.MalformedCatalogFileException` |
| 4 composition | `server.config.exception.MissingServerDirectoryException` |

## Dependency injection

The container is avaje-inject (ADR-0016), with `jakarta.inject` for the standard annotations. It generates its wiring at
compile time, so a missing or ambiguous bean fails the build and names the type.

| Ring | DI annotations | Where |
|---|---|---|
| 1 entities | none | `voyager-api`, `voyager-physics` |
| 2 use cases | none; constructor parameters only | `voyager-race` |
| 3 adapters | none; a `@Bean` method in the composition root calls the constructor | `voyager-platform` |
| 4 composition | `jakarta.inject`, avaje `@Factory`, `@Bean`, `@External`, `BeanScope` | `inject` packages, and the entry points `VoyagerServer` and `SetupServer` |

The rules that follow:

- **One composition root per deployable.** `voyager-server` for the game server, `voyager-setup` for the setup server.
  They do not share a root, and `voyager-setup` never imports `voyager-server`.
- **One factory per slice group.** `ServerBeans` and `CupBeans` in `server.inject` exist today; `CupBeans` wires the cup,
  and the follow-up splits the rest by slice (`RunBeans`, `FlightBeans`). A factory contains only `@Bean` methods that call
  constructors.
- **An ordered pipeline.** The per-tick steps are one unmodifiable list in a `TickPipeline`, built by one `@Bean` method
  (`CupBeans.tickPipeline`) and run by `VoyagerServer` at the start of each tick. The order is flight sampling, then the
  boost burn counter, then the phase advance. The golden master and `TickPipelineTest` pin it. Injected collections are not
  used for steps, because their order is not guaranteed.
- **Tests construct with `new`.** A unit test builds its objects by hand. Only a composition-root test builds a
  `BeanScope`, and it builds a fresh one per test, so no test depends on another.
- **No service locator and no static singletons.** A class receives its collaborators through its constructor. A
  process-wide object is one instance that the composition root creates and passes to its users. A mutable static
  field is forbidden in every rebuild module.

The composition root's `@Bean` methods are wiring. When a `@Bean` method computes a value other than a constructor
argument, the logic belongs in a slice.

## Enforcement

Each rule is either enforced by a rule that fails the build, or named as a gap with the follow-up that closes it.

**Enforced today** (`voyager-fitness`):

| Requirement | Rule |
|---|---|
| Entities and use cases have no framework type or file I/O | `ApiPurityTest`: `apiDoesNotDependOnMinestomOrItsWorldLoader`, `apiDoesNotDependOnPaper`, `apiDoesNotDependOnXerus`, `apiDoesNotPerformFileIo`, `physicsDoesNotDependOnMinestomOrItsWorldLoader`, `physicsDoesNotPerformFileIo`, `raceDoesNotDependOnMinestomXerusOrItsWorldLoader`, `raceDoesNotDependOnPaper` and the other purity rules in that class |
| Dependencies point inward (partial) | `apiDoesNotDependOnPlatform`, `physicsDoesNotDependOnRaceOrPlatform`, `raceDoesNotDependOnPlatform`, `platformDoesNotDependOnServer` |
| Platform types confined to their converters | `onlyBlockShapesReachesForMinestomsShapeImplementation`, `onlyVelocityExitSendsAVelocityToMinestom`, `onlyVectorsBuildsAMinestomVectorFromDomainCoordinates`, `onlyPlatformDependsOnGson`, `onlyTheTextPackageBuildsAUserFacingString` |
| DI boundary (partial) | `apiDoesNotDependOnADiContainer`, `physicsDoesNotDependOnADiContainer`, `onlyServerDependsOnDiContainer` |
| Domain values and exceptions | `DesignRuleTest`: `apiTypesAreRecordsInterfacesOrEnums`, `exceptionsAreUncheckedAndDomainNamed`, `exceptionsLiveInAnExceptionSubpackage`, `race_domain_exceptions_are_runtime_exceptions` |
| Package-level `@NotNullByDefault` | `NullabilityConventionTest` |
| No vacuous pass; every module covered | `FitnessCoverageTest` |
| Known slice-boundary violations are frozen, and a new one fails the build | `SliceBoundaryRulesTest`: rules R3 to R6 are frozen against the committed store in `voyager-fitness/src/test/resources/archunit_store/`; on CI the store cannot be created or updated. A closed item cannot return: `ClosedViolationsAreGoneTest` |
| Slice cycles are forbidden, in `race`, `api` and `platform` | `SliceBoundaryRulesTest`: `r7_raceSlicesAreFreeOfCycles` and `r8_apiSlicesAreFreeOfCycles` are plain; `r4_platformSlicesAreFreeOfCycles` is frozen (item 27) |
| The server holds no scoring or cup import, and no Minestom outside its composition root | `r1_serverDoesNotDependOnRaceScoringOrCup` (plain); `r2_serverOutsideTheCompositionRootDoesNotUseMinestom` (plain) |
| The race command is built only by the composition root | `CompositionRootRulesTest`: `raceCommandIsConstructedOnlyByTheCompositionRoot` (plain) |
| The cup's observable behaviour is pinned tick by tick | `CupSessionGoldenMasterTest` (server module): four scenarios against committed golden files, which a move never regenerates |
| Platform infrastructure does not depend on race | `r3_platformInfrastructureDoesNotDependOnRace` (frozen, items 13, 15 and 20) |
| Mapsetup contracts live in the mapsetup slice | `r5_mapsetupContractsLiveInPlatformMapsetup` (frozen, items 24 and 29) |
| Setup adapter code holds no Minestom; the composition root is excluded | `r6_setupAdapterDoesNotUseMinestom` (frozen, item 26) |

**Proposed for `add-architecture-slice-rules`** (`test(fitness)`, each with `allowEmptyShould(false)`): a layered rule
for the four rings; a generated rule that forbids access to each slice's `internal` package; DI placement rules that name
the composition roots; and a rule against mutable static fields and clock reads in the inner rings.

**Named gap.** The placement of the race-run holder and the map transition in `platform.cup` (ADR-0023) is enforced by
review. `ClosedViolationsAreGoneTest` stops the stored `RaceRuns` violations from returning, but no rule forbids
`platform.world` from depending on `platform.cup`.

A rule enters the suite in one of two ways. It is plain when it passes on `main`. It is frozen when its known violations
are recorded in a committed baseline, so that only a new violation fails the build. The baseline is refreshed as
[the how-to guide](../guides/how-to-refresh-the-architecture-baseline.md) describes, and the choice is recorded in
[ADR-0020](../decisions/0020-freeze-architecture-violations-as-baseline.md). A rule is never excluded from the build, or
narrowed, only to make it pass.

## Migration status

The rebuild does not follow every rule yet. The change's `design.md` lists each violation with file and line evidence,
numbered 1 to 27, and marks it open, resolved or acceptable. The change `extract-cup-slice` closed items 1, 2, 3, 5, 6, 7,
8, 9 and 10, and addendum 28 (`CupSession` imports Minestom). The change `move-race-run-holder` closed items 11 and 12. At
the time of writing, 11 violations are open, 13 are resolved, and three are acceptable as they stand (item 22 among them,
as a package move only). The open items fall into
three follow-ups:

1. `add-architecture-slice-rules` (`test(fitness)`): the proposed rules. Starts after the violations below are fixed.
2. Done by `extract-cup-slice`: the cup flow and the server's game package. `server.game` no longer exists. Item 23
   (`CupResolution`) stays open, because its rule needs a port first.
3. `regroup-platform-by-slice` (`refactor(platform)`): moves slice classes out of the technical packages, and breaks the
   `catalog` and `world` package cycle. Items 11 and 12 (the race-run holder and the map transition) are done by
   `move-race-run-holder`; the rest of the list is open.
4. `flatten-api-by-slice` (`refactor(api)`): splits the flat `api.race` package and `api.physics` by slice.

A fifth follow-up, `regroup-setup-adapters`, would move the Minestom adapter code of `voyager-setup` into the platform.
It needs the owner's approval before it starts.

Six open items are not expressible as dependency rules, and no rule guards them: items 14, 16, 17, 18, 23 and
25. They are tracked here and in the migration list only, and a regression in them is caught by review. Every other open
item that a dependency rule can express is recorded in the baseline with its item number. The mapping, and the provisional
addenda 28 (`CupSession` imports Minestom) and 29 (`MapDraftAdapter` depends on `api.mapsetup`), are in the design of
`freeze-slice-boundary-violations`, section D7.

Each follow-up updates the slice table in this page and in `design.md` in the same pull request.

## Related reading

- [ADR-0017](../decisions/0017-clean-architecture-with-vertical-slices.md): the decision and its alternatives.
- [ADR-0016](../decisions/0016-replace-guice-with-avaje-inject.md): the DI container.
- `docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`: the module cut (D8), the DI rule (D10) and the
  delivery plan.
- `docs/reference/semantic-anchors.md`: the exact names "Clean Architecture" and "Vertical Slice Architecture (VSA)".
