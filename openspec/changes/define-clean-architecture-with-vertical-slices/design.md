# Design

## Context

The rebuild has five production modules (`voyager-api`, `voyager-physics`, `voyager-race`, `voyager-platform`,
`voyager-server`) and a test module (`voyager-fitness`). See `proposal.md` for why the rules are needed now. The
greenfield design (`docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`, decision D8 and the "Module
architecture" section) fixes the module graph. The DI decision D10 and the avaje change (`switch-di-to-avaje-inject`,
proposal and design) fix the container. This design adds the layer rules, the slice rules and the package mapping on
top of both.

Facts that shape the design, verified in the code on `main`:

- The module graph matches the greenfield design. `voyager-api` depends on the JDK and `org.jetbrains` annotations only.
  `voyager-physics` and `voyager-race` depend on `api`. `voyager-platform` depends on `api`, `physics` and `race`.
  `voyager-server` depends on all of them and still uses Guice.
- Inside the modules, packages are already slice-shaped in `race` (`collision`, `effect`, `flow`, `line`, `progress`,
  `run`, `scoring`) and in `platform` (`flight`, `hud`, `catalog`, `collision`). Other platform packages are technical
  (`render`, `text`, `tick`, `world`, `convert`).
- The slice graphs inside `race` and `platform` are acyclic. One edge would close a cycle if `progress` stayed a
  separate package from `run` (see Decision 2).
- `voyager-server` holds the cup use case: `server/game/CupSession.java` is 770 lines and computes scores and standings.
- The per-tick order is written in code, in `CupSession.tick()` (`CupSession.java:291-302`): flight sampling, boost
  advance, then the phase driver's `onUpdate()`.
- No mutable static field, no clock read (`System.currentTimeMillis`, `System.nanoTime`, `Instant.now`) and no DI
  annotation exists yet in `voyager-api`, `voyager-physics`, `voyager-race` or `voyager-platform` `src/main`.

## Goals / Non-Goals

**Goals:**
- One written target for the rings (Clean Architecture) and the slices (VSA), covering every current package.
- A slice-by-module package table that a contributor can use to place a new class.
- A DI rule per ring, consistent with `switch-di-to-avaje-inject`.
- Each rule mapped to an existing or proposed ArchUnit rule (see `specs/architecture/architecture-enforcement`).
- A migration list with file and line evidence, so the follow-up changes start from facts.

**Non-Goals:**
- No code, package, or test moves in this change. Every move is a follow-up with its own type.
- No new fitness rules in this change. They belong to `test(fitness)` in `add-architecture-slice-rules`.
- No decision on the avaje bootstrap details. `server/dependency-injection` (switch-di change) owns them.
- No change to `voyager-setup` beyond naming its future slice, and no change to the tree being replaced.

## Decisions

### 1. Rings are Gradle modules (Clean Architecture)

Dependencies point inward, toward `voyager-api`. Four rings:

```
  ring 4  composition root   voyager-server          voyager-setup (later)
            |  wires every ring; the only code that names every concrete type
            v
  ring 3  adapters &         voyager-platform        voyager-persistence (later)
          frameworks         (Minestom, Xerus, Gson, Falco, JDBC later)
            |  implements ports declared inward; converts to and from domain values
            v
  ring 2  use cases          voyager-race
            |  run, flow, scoring, cup, ring, line
            v
  ring 1  entities           voyager-physics  --->  voyager-api
          (pure)             (flight simulation)    (values, records, ports)
```

Allowed edges (the table is normative in `specs/architecture/module-rings`):

```
voyager-physics       -> voyager-api
voyager-race          -> voyager-api, voyager-physics
voyager-platform      -> voyager-api, voyager-physics, voyager-race
voyager-persistence   -> voyager-api
voyager-setup         -> voyager-api, voyager-platform
voyager-server        -> every rebuild module
```

Why rings are modules and not packages: Gradle enforces the edges at compile time, and the ArchUnit rules only have to
check the framework-type confinement. The alternative, one module with packages for each ring, leaves the direction to
review and to ArchUnit alone. The alternative of one module per slice multiplies the Gradle graph beyond the eight modules
that D8 chose, and it would force each Minestom adapter to live in several modules.

Ring 3 and ring 4 are separate rings but platform is one module for both adapters and frameworks. Minestom is confined to
that module, so the module boundary is the enforced line between ring 3 and the rest. The sub-packages of platform are
slices and infrastructure (Decision 4), not rings.

### 2. Slices are packages inside each module (VSA)

A slice is a feature column. It owns its use-case types, its ports and its adapters in every ring it touches. Its packages
are `net.elytrarace.voyager.<module>.<slice>..`. A module with exactly one slice uses its root package as that slice.
Here that is `voyager-physics`: its root package is the flight slice, and `collision`, `step` and `math` are technical
layers inside it.

Slice list (the nine current feature slices plus two future ones):

| Slice | Feature | Notes |
|---|---|---|
| `flight` | Elytra flight simulation, collision space, firework boost, flight tracking | `physics` root in `voyager-physics` |
| `ring` | Ring passes, progress through rings, ring effects | Absorbs `race.collision`, `race.progress`, `race.effect` |
| `run` | One racer's run through a map | Orchestrates `flow`, `ring`, `scoring` |
| `flow` | Race phases, clock, timings | State machine is pure; Xerus driver is platform |
| `scoring` | Map and cup scores, medals, placement | Pure |
| `cup` | A cup: maps in order, standings, map transitions | New `race.cup`; today's logic is in `server.game` |
| `line` | Racing line through a map | Catmull-Rom path, renderer in platform |
| `catalog` | Map and cup definitions and their JSON files | Ports in api; JSON adapters in platform |
| `hud` | What a player sees in the race (boards, feedback, countdown) | Platform only |
| `record` (future) | Persisted runs and profiles | `voyager-persistence` (E5) |
| `mapsetup` (future) | Creating and editing maps | `voyager-setup` (E6) |

### 3. Slice by module: the package table

Packages are the current ones. "Target" is where the class belongs after the follow-up moves in the migration list.
Cells with "-" mean the slice has no package in that module.

| Slice | `voyager-api` (ring 1) | `voyager-physics` (ring 1) | `voyager-race` (ring 2) | `voyager-platform` (ring 3) | `voyager-server` (ring 4) | Future |
|---|---|---|---|---|---|---|
| `flight` | `api.physics` (CollisionSpace, FlightInput, FlightState, exception). Target `api.flight`; `api.race.BoostConfig` and `InvalidBoostConfigException` also move there | `physics`, `physics.collision`, `physics.step`, `physics.math` | - | `platform.flight` (FlightTracker, FireworkBoostTracker, FlightTransition, FlightEndReason); `platform.collision` (MinestomCollisionSpace, BlockShapes, BlockCursor) and `platform.tick` (FlightTick, FlightTickDriver, FlightSample, FlightSampler, UntrackedFlightException): target `platform.flight` | `server.game` Rockets, LivePlayerSampler, Racers: target `platform.flight` | - |
| `ring` | `api.race` (Ring, RingType), `api.race.effect`, `api.race.exception` (InvalidRingException). Target `api.ring` | - | `race.collision` (RingPass), `race.progress` (ProgressTracker, ProgressUpdate, RingProgress), `race.effect` (RingEffectRegistry, SpeedMultiplierEffect). Target `race.ring` | - (RingAdapter lives in `platform.catalog.adapter`, owned by catalog) | - | - |
| `run` | - | - | `race.run` (RaceRun) | `platform.world.RaceRuns`: target `platform.run` | - | `persistence.record` |
| `flow` | `api.race.GameMode` (kernel) | - | `race.flow` (RaceStateMachine, RaceState, RacePhase, RaceClock, RaceTimings, exception) | `platform.tick` XerusPhaseDriver, RacePhaseListener: target `platform.flow` | `server.config.ServerSettings` reads RaceTimings (config, allowed) | - |
| `scoring` | `api.race` (MedalTier, MedalBrackets). Target `api.scoring` | - | `race.scoring` (MapScorer, MapScore, CupScorer, CupScore, MedalCountdown, MedalOutlook, Placement, PlacementBonus) | Consumers only: `platform.text.Messages`, `platform.hud.HudState` read MedalOutlook | `server.game` CupStandings, CupStanding: target `race.cup` | - |
| `cup` | `api.race` (CupDefinition, CupCatalog port), `api.race.exception.InvalidCupException`. Target `api.cup` | - | New `race.cup`: cup progression, standings, the logic of CupSession, CupResolution | `platform.world.MapTransition`: target `platform.cup` | `server.game` CupSession, CupResolution, exception/UnresolvedCupException: target `race.cup`. The wiring that stays is `VoyagerServer` | - |
| `line` | `api.race` (GuideLine, GuidePoint), `api.race.exception` (InvalidGuideLineException). Target `api.line` | - | `race.line` (RacingLine, CatmullRom) | `platform.render.GuideLineRenderer`: target `platform.line` | - | - |
| `catalog` | `api.race` (MapCatalog, MapDefinition), `api.race.exception` (InvalidMapException). Target `api.catalog` | - | - | `platform.catalog` (JsonMapCatalog, JsonCupCatalog, CatalogDirectory, CatalogConsistency, adapter/, exception/) | `server.game.CupResolution` must depend on the `CupCatalog` port, not `JsonCupCatalog` | `setup.mapsetup` |
| `hud` | - | - | - | `platform.hud` (HudState, RaceHud, RaceFeedback, StartCountdown) | - | - |
| Kernel | `api.math` (Vec3, Aabb, exception); `api.race` root holds `GameMode` | - | - | - | - | - |
| Infrastructure | - | - | `race` root (RaceCore marker) | `platform` root (PlatformCore); `platform.text`; `platform.convert` (Vectors, VelocityExit); `platform.world` (MapInstances, WorldHealth); `platform.tick` (driver base) | `server` (VoyagerServer); `server.command` (RaceCommand); `server.config` (ServerSettings); `server.inject` (VoyagerModule, to be replaced by ServerBeans and RaceBeans) | `persistence` root and infrastructure |

Two notes on the table:
- `api.physics` is a module-named package that holds the flight slice's contract. Its rename to `api.flight` is in
  follow-up 4 and is optional. The rule in the spec is about the slice, not the name.
- `GameMode` is the only type in `api.race` root that stays there after the split. It is the kernel discriminator that
  four slices share.

### 4. Technical packages: slice or infrastructure

Rule: a package named for a feature is a slice; a package named for a technical role is infrastructure and may serve
several slices but owns no use-case type.

| Package | Classification | Decision |
|---|---|---|
| `platform.render` | Technical name, one feature inside | Not infrastructure. Its only class renders the racing line: move into `platform.line` (follow-up 3) |
| `platform.text` | Infrastructure (message bundles, palette, time format) | Stays. Slice-specific keys stay in the file that uses them. It is consumed by `hud` and `server` |
| `platform.hud` | Slice `hud` | Stays |
| `platform.tick` | Infrastructure (the tick driver base) | Stays as infrastructure. Its flight classes move to `platform.flight` and its phase classes to `platform.flow` (follow-up 3) |
| `platform.world` | Infrastructure (instance and world health) | Stays for MapInstances and WorldHealth. RaceRuns moves to `platform.run` and MapTransition to `platform.cup` (follow-up 3) |
| `platform.convert` | Infrastructure (domain to Minestom value conversion) | Stays. It is the only place that converts values |
| `platform.collision` | Slice `flight` (CollisionSpace adapter) | Moves into `platform.flight` (follow-up 3) |
| `platform.catalog` | Slice `catalog` | Stays |
| `platform.flight` | Slice `flight` | Stays |
| `server.game` | Not a slice and not infrastructure: a mix of cup use case and Minestom adapters | Dissolved by follow-up 2 into `race.cup`, `platform.flight`, `platform.world` |
| `server.command` | Infrastructure (inbound command adapter) | Stays. Each command calls a use case in a slice |
| `server.config` | Infrastructure (configuration) | Stays |
| `server.inject` | Infrastructure (composition) | Stays. Holds the factories described in Decision 7 |

### 5. Ports, data and exceptions per ring

**Ports.** A port is an interface that an inner ring calls and an outer ring implements. It is declared in the innermost
module that calls it. It is declared in `voyager-api` when more than one module calls it. Examples: `CollisionSpace`
(called by physics, implemented by platform) lives in `api.physics`. `CupCatalog` and `MapCatalog` (called by race and
server, implemented by platform) live in `api.cup` and `api.catalog`. A port never lives in the ring that implements it.

**Data.** Data crosses a boundary as an immutable record or enum. The records of `voyager-api` are the transfer format
between rings. They carry no Minestom, Adventure, Gson, Falco or JDBC type. A conversion from a platform value to a domain
value happens in `voyager-platform` only (`Vectors` for `Vec` to `Vec3`, `VelocityExit` for the velocity write). Domain
records validate in their compact constructor (`Vec3` rejects NaN and infinity).

**Exceptions per ring.** Each exception sits in an `exception` subpackage next to the code that raises it, with a
`package-info.java`, extends `RuntimeException`, and ends with `Exception`:

| Ring | Where the exception lives | Example today |
|---|---|---|
| 1 entities | `api.<slice>.exception`, `api.math.exception`, `physics.exception` | `api.math.exception.NonFiniteVectorException`, `physics.exception.InvalidSimulationStateException` |
| 2 use cases | `race.<slice>.exception` | `race.flow.exception.IllegalPhaseTransitionException` |
| 3 adapters | `platform.<slice or infra>.exception` | `platform.catalog.exception.MalformedCatalogFileException` |
| 4 composition | `server.<area>.exception` | `server.config.exception.MissingServerDirectoryException` |

An adapter translates external failures (I/O, Minestom, Gson, Falco) into exceptions of its own ring before they reach
an inner ring. Inner rings never catch an outer exception. The exception `server.game.exception.UnresolvedCupException`
is a use-case exception in ring 4 and moves into `race.cup.exception` (follow-up 2).

### 6. Cross-slice communication and the shared kernel

- **Calls.** A slice calls another slice through the other slice's public types: its root package and its `exception`
  package. A helper that must stay hidden lives in `<slice>.internal`, and no class outside the slice imports it.
- **Direction.** The slice graph inside a module is a DAG. Inside `race`, today's edges are `run` to `flow`, `ring`,
  `scoring`, and `scoring` to `ring`. `ring` depends on nothing else in race. Progress is part of `ring`, which is what
  makes the graph acyclic: if `progress` were a separate `run` package, `run -> scoring -> progress -> ...` would form a
  cycle with `RaceRun` (`race/run/RaceRun.java:10`, `race/scoring/MapScorer.java:7`).
- **Events.** Inside a tick, slices talk through method calls that the composition root sequences. There is no shared
  mutable state between slices.
- **Shared kernel.** `api.math` (vectors and boxes) and the `GameMode` discriminator. Kernel types are immutable and
  platform-free. A change to the kernel needs an ADR, because every slice sees it.

### 7. Dependency injection (avaje-inject and jakarta.inject)

| Ring | Annotations allowed | Where |
|---|---|---|
| 1 entities | None | `voyager-api`, `voyager-physics` |
| 2 use cases | None | `voyager-race`. Classes take constructor parameters and nothing else |
| 3 adapters | None. Classes are constructed by `@Bean` methods of the composition root | `voyager-platform`; later `voyager-persistence`. No `@Inject`, `@Singleton`, `@Named` or avaje type |
| 4 composition | `jakarta.inject`, avaje `@Factory`, `@Bean`, `BeanScope` | `voyager-server` `inject` package and `VoyagerServer`; later `voyager-setup` |

Spike facts from `switch-di-to-avaje-inject` (task 1, passed 2026-10-09) that bind this table:
- Avaje wires a class only when it carries `@Singleton` (or `@Component`) and is compiled in the composition root.
  `@Inject` alone is not picked up. A class from another module is wired only by a `@Bean` method, which is why ring 3
  needs no annotation at all.
- A missing bean fails `compileJava` and names the type. An ambiguous bean fails too, but the message names the
  conflicting `@Bean` methods, not the type.

Rules that follow from the table:
- **One composition root per deployable.** `voyager-server` now, `voyager-setup` when it exists. They do not share a root.
- **One factory per slice group.** The server's `inject` package holds `@Factory` classes named for slices, for example
  `RunBeans`, `CupBeans`, `FlightBeans`. The switch-di change creates `ServerBeans` and `RaceBeans` first, and the
  follow-up split is a refactor of those two classes, not a new design.
- **Ordered pipeline.** The per-tick steps are one unmodifiable `List`, built in one `@Bean` method and passed to the
  tick. The order today is the order in `CupSession.tick()`: flight sampling (`CupSession.java:292`), boost advance (`:297`),
  phase advance (`:302`). A test asserts that order. A collection injection of the steps is not used, because the order of
  collection injection is not guaranteed.
- **Tests.** Unit tests construct with `new`. Composition-root tests build a fresh `BeanScope` per test on a `@TempDir`.
  No static field holds a scope.
- **No service locator.** Only composition-root code may call a container. A class that calls a container lookup is a
  service locator, and the rule forbids it.
- **No static singletons.** A process-wide object is one instance created by the composition root, passed to its users.
  Static methods are allowed only as pure functions or as factories of an abstract utility class with a private
  constructor (ManisGame rule 2).

**Alternatives considered.** Keep Guice: rejected in the switch-di change (D10). Manual wiring in one method: viable, and
the spec is written so that it would satisfy the same rules. Dagger: rejected in the switch-di change. Let each slice own
its own container: rejected because it puts container types inside the slice rings.

### 8. Enforcement mapping

Existing rules named in `voyager-fitness` (all in `ApiPurityTest` unless marked):

| Requirement | Existing rule |
|---|---|
| Entities and use cases have no framework types | `apiDoesNotDependOnMinestomOrItsWorldLoader`, `apiDoesNotDependOnPaper`, `apiDoesNotDependOnXerus`, `apiDoesNotDependOnPersistenceTechnology`, `apiDoesNotPerformFileIo`, `physicsDoesNotDependOnMinestomOrItsWorldLoader`, `physicsDoesNotDependOnXerus`, `physicsDoesNotPerformFileIo`, `raceDoesNotDependOnMinestomXerusOrItsWorldLoader`, `raceDoesNotDependOnPaper`, `raceDoesNotDependOnPersistenceTechnology` |
| Dependencies point inward (partial) | `apiDoesNotDependOnPlatform`, `physicsDoesNotDependOnRaceOrPlatform`, `raceDoesNotDependOnPlatform`, `platformDoesNotDependOnServer` |
| Platform types confined to converters | `onlyBlockShapesReachesForMinestomsShapeImplementation`, `onlyVelocityExitSendsAVelocityToMinestom`, `onlyVectorsBuildsAMinestomVectorFromDomainCoordinates`, `onlyPlatformDependsOnGson`, `onlyTheTextPackageBuildsAUserFacingString` |
| DI boundary (partial) | `apiDoesNotDependOnADiContainer`, `physicsDoesNotDependOnADiContainer`, `onlyServerDependsOnDiContainer` (Guice today; avaje after switch-di) |
| Domain values and exceptions | `DesignRuleTest`: `apiTypesAreRecordsInterfacesOrEnums`, `exceptionsAreUncheckedAndDomainNamed`, `exceptionsLiveInAnExceptionSubpackage`, `race_domain_exceptions_are_runtime_exceptions` |
| Package-level annotations | `NullabilityConventionTest` |
| No vacuous pass, every module covered | `FitnessCoverageTest` |

Proposed rules for `add-architecture-slice-rules` (each with `allowEmptyShould(false)`):

| Requirement | Proposed rule |
|---|---|
| Ring order | `layeredArchitecture()` with layers Entities (`..api..`, `..physics..`), UseCases (`..race..`), Adapters (`..platform..`, `..persistence..`), Composition (`..server..`, `..setup..`) |
| Slice cycles | `slices().matching("net.elytrarace.voyager.race.(*)..").should().beFreeOfCycles()`, the same for platform's feature slices and for `api.(*)` |
| Slice internals | Generated per slice: `noClasses().that().resideOutsideOfPackage(<slice>..).should().dependOnClassesThat().resideInAPackage(<slice>.internal..)` |
| DI placement | `io.avaje.inject..` and `jakarta.inject..` forbidden outside `..server..` and `..setup..`; no DI annotation on any platform class; `BeanScope` only in `..server.inject..` and `VoyagerServer` |
| No mutable statics, no clock reads | Non-final static field rule for all modules; `callMethod` rules for `System.currentTimeMillis`, `System.nanoTime`, `Instant.now` in api, physics, race |
| Server holds no scoring after the move | After follow-up 2: `noClasses().that().resideInAPackage("..server..").should().dependOnClassesThat().resideInAPackage("..race.scoring..")` |

ArchUnit calls named above must be checked against ArchUnit 1.4.2 during apply. Where the exact method is not in that
version, the rule uses a predicate that is.

## Current violations (migration list)

These are real findings in `main`. Each line is a candidate for one of the follow-up changes. None is fixed here.

**Composition root holds use-case logic and platform adapters (ring 4):**
1. `voyager-server/src/main/java/net/elytrarace/voyager/server/game/CupSession.java:33-36` imports `race.scoring`, and the
   cup use case is computed there: `MapScorer.score` at `:498`, `standings.scoreOn` at `:607`, `MedalCountdown.outlook` at
   `:685`. The file is 770 lines. Target `race.cup`, with wiring left in `VoyagerServer`. Follow-up 2.
2. `voyager-server/src/main/java/net/elytrarace/voyager/server/game/CupStandings.java:4-8` imports five `race.scoring`
   types (`:4-8`) and uses `GameMode` (`:3`) to compute standings. Target `race.cup`. Follow-up 2.
3. `voyager-server/src/main/java/net/elytrarace/voyager/server/game/CupStanding.java:3` imports `race.scoring.CupScore`.
   Target `race.cup`. Follow-up 2.
4. `voyager-server/src/main/java/net/elytrarace/voyager/server/game/CupResolution.java:4` depends on the concrete
   `platform.catalog.JsonCupCatalog`, not on the `CupCatalog` port. Fix: take the port. Follow-up 2.
5. `voyager-server/src/main/java/net/elytrarace/voyager/server/game/exception/UnresolvedCupException.java` is a use-case
   exception in ring 4. Target `race.cup.exception`. Follow-up 2.
6. `voyager-server/src/main/java/net/elytrarace/voyager/server/game/Racers.java:6-11` uses Minestom players and
   `platform.convert`. Target `platform.flight`. Follow-up 2.
7. `voyager-server/src/main/java/net/elytrarace/voyager/server/game/Rockets.java:4-9` uses Minestom firework entities.
   Target `platform.flight`. Follow-up 2.
8. `voyager-server/src/main/java/net/elytrarace/voyager/server/game/LivePlayerSampler.java:5-10` uses `platform.flight` and
   `platform.tick`. Target `platform.flight`. Follow-up 2.
9. `voyager-server/src/main/java/net/elytrarace/voyager/server/game/CurrentMapBlocks.java:3-5` uses Minestom instances and
   blocks. Target `platform.world`. Follow-up 2.
10. `voyager-server/src/main/java/net/elytrarace/voyager/server/game/CupSession.java:291-302` defines the tick order in the body
    of `tick()`. Target: the explicit ordered list in the composition root (switch-di task 4.1). Follow-up 2 or the avaje change.

**Technical packages with slice content (ring 3):**
11. `voyager-platform/src/main/java/net/elytrarace/voyager/platform/world/RaceRuns.java:6-7` imports `race.flow.RaceClock`
    and `race.run.RaceRun`. Target `platform.run`. Follow-up 3.
12. `voyager-platform/src/main/java/net/elytrarace/voyager/platform/world/MapTransition.java:3-4` is cup behaviour in the
    world package. Target `platform.cup`. Follow-up 3.
13. `voyager-platform/src/main/java/net/elytrarace/voyager/platform/tick/XerusPhaseDriver.java:4-8` and
    `platform/tick/RacePhaseListener.java:3` hold the flow slice. Target `platform.flow`. Follow-up 3.
14. `voyager-platform/src/main/java/net/elytrarace/voyager/platform/tick/FlightTick.java:5` and
    `platform/tick/FlightTickDriver.java:6-8` hold the flight slice. Target `platform.flight`. Follow-up 3.
15. `voyager-platform/src/main/java/net/elytrarace/voyager/platform/render/GuideLineRenderer.java:6` imports `race.line`
    from a `render` package. Target `platform.line`. Follow-up 3.
16. `voyager-platform/src/main/java/net/elytrarace/voyager/platform/collision/MinestomCollisionSpace.java` is the flight
    slice's `CollisionSpace` adapter in its own package. Target `platform.flight`. Follow-up 3.

**Slice split across packages (ring 2):**
17. `voyager-race/src/main/java/net/elytrarace/voyager/race/collision/RingPass.java`,
    `race/progress/ProgressTracker.java:6` (imports `race.collision.RingPass`), and `race/effect/` are one slice, `ring`,
    in three packages. Target `race.ring`. Follow-up 3 (or the rename is part of the next `race` change).

**Flat contract package (ring 1):**
18. `voyager-api/src/main/java/net/elytrarace/voyager/api/race/` holds 12 types from six slices plus the kernel:
    `Ring`, `RingType`, `MapDefinition`, `MapCatalog`, `CupDefinition`, `CupCatalog`, `GuideLine`, `GuidePoint`,
    `MedalTier`, `MedalBrackets`, `BoostConfig`, `GameMode`. Target `api.<slice>` and `api.race` root with `GameMode` only.
    Follow-up 4. `voyager-api/src/main/java/net/elytrarace/voyager/api/physics/` moves to `api.flight` in the same follow-up.

**Acceptable as they are, listed for completeness:**
19. `voyager-race/src/main/java/net/elytrarace/voyager/race/run/RaceRun.java:6-10` imports `flow`, `ring` and `scoring`.
    This is the run slice composing the others through their public types. It is allowed by Decision 6 and needs no change.
20. `voyager-platform/src/main/java/net/elytrarace/voyager/platform/text/Messages.java:4` and
    `platform/hud/HudState.java:3` import `race.scoring.MedalOutlook`. Adapters may depend on the inner ring. No change.
21. `voyager-server/src/main/java/net/elytrarace/voyager/server/inject/VoyagerModule.java:3-5` and
    `voyager-server/src/main/java/net/elytrarace/voyager/server/VoyagerServer.java:3-5,156` use Guice. The switch-di change
    replaces them. Not this change's concern.

**Checked and clean:**
- No Minestom, Xerus, Paper, Gson, Falco, `jakarta.inject`, `com.google.inject`, or `io.airlift` import in `voyager-api`,
  `voyager-physics` or `voyager-race` `src/main`. No `platform` or `server` import in those modules.
- No `platform` import of `server`.
- No mutable static field and no clock read in `voyager-api`, `voyager-physics`, `voyager-race` or `voyager-platform`
  `src/main`.
- No cycle between slices of `race` or `platform`, with `progress` counted as part of `ring`.

## Risks / Trade-offs

- **[The table and the code drift apart] -> Each follow-up updates the table in this file and in `docs/explanation/architecture.md` in the same PR.** The ArchUnit slice rules in follow-up 1 turn the table into a check for cycles and internals.
- **[Folding progress into ring makes ring bigger] -> Accepted.** Ring-pass tracking, progress and effects change together; a separate package would only add an edge that a review must justify.
- **[Rules added before the moves go red] -> The requirement "Rules enter the suite only after the violations they flag are fixed" in `architecture-enforcement`.** Follow-ups 2 and 3 precede the rules that they would break.
- **[The composition root's tick order is wiring, but a reviewer may read it as logic] -> The spec names the order as wiring, and the test asserts it.** Only the order is in the root; each step's logic stays in its slice.
- **[Ring 3 and ring 4 share one module] -> The module boundary is the enforced line; the sub-package split is a convention checked by the layered rule (Composition vs Adapters).**
- **[A slice split later would move many imports] -> Follow-up 4 is one `refactor(api)` change, done before more code depends on `api.race`.**
- **[The ArchUnit method names in Decision 8 may differ in 1.4.2] -> Verify during apply; the requirement stays the same.**

## Migration Plan

This change ships documentation only. The order of the work that follows:

1. This change: specs, design, ADR-0017, explanation page, pointers. Merged as `docs(architecture)`. No behaviour changes.
2. Follow-up 1, `add-architecture-slice-rules` (`test(fitness)`): adds the proposed rules whose violations are already fixed, and records the rest as held back.
3. Follow-up 2, `move-cup-flow-out-of-server` (`refactor(server)`): items 1 to 10.
4. Follow-up 3, `regroup-platform-by-slice` (`refactor(platform)`): items 11 to 17.
5. Follow-up 4, `flatten-api-by-slice` (`refactor(api)`): item 18.
6. The avaje change (`refactor(server)`) lands whenever it is approved. Its `ServerBeans` and `RaceBeans` are split into per-slice factories in follow-up 2 (items 9 and 10 touch the same classes).

Rollback: each follow-up is one squash commit on `main` and reverts cleanly. This change has no code to revert. The ADR is
Proposed until the user accepts it, and its status is then changed in the same PR that accepts it.

## Open Questions

Deferrable: none of these changes this change's specs or tasks. Each is decided in the follow-up that needs it.

- Whether `RaceRun` should depend on `ring` through a port, if `run` and `ring` need to be separated later (follow-up 3).
- Whether `api.physics` is renamed to `api.flight` or kept as the module name (follow-up 4 decides).
- Whether `voyager-setup`'s composition root shares a `@Factory` base type with `voyager-server` (greenfield open question; deferred until `voyager-setup` exists).

Human checkpoints (decisions that change the follow-ups, so they are tasks in `tasks.md`, not open questions here):
slice names `ring`, `cup`, `catalog`; moving `server/game` into `race.cup`; renaming `api.physics`. Each needs the user's
approval before a follow-up starts.

Decided by the user on 2026-10-09:
- The cup flow (`CupSession`, `CupStandings`, `CupResolution`) moves from `server/game` into a new `race.cup` slice;
  `voyager-server` keeps only wiring.
- `progress` folds into the `ring` slice together with `collision` and `effect`.
- Order: `switch-di-to-avaje-inject` is applied first; this change follows.

Still open for checkpoint 1.1: the slice names `catalog` and `hud`, and renaming `api.physics` to `api.flight`.

## Documentation

- `docs/decisions/0017-clean-architecture-with-vertical-slices.md` (MADR 4.0). Number 0017: `main` ends at 0011, 0012 to
  0015 are on the unmerged `refactor/architecture-ratchet` branch, and 0016 is reserved for the avaje decision. The ADR
  cites ADR-0016 (`switch-di-to-avaje-inject`) and greenfield D8 and D10.
- `docs/explanation/architecture.md` (Diataxis "explanation"; the folder is new, because `docs/` has no explanation pages
  yet). It explains the rings, the slices, the table and the DI rules for a reader who wants the reasoning, and points to
  the spec for the normative statements.
- `CLAUDE.md`, section "Architecture": one pointer line to the explanation page and ADR-0017. Nothing else in `CLAUDE.md` changes here.
- `docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`: a short cross-reference in "Module architecture" to
  ADR-0017, the explanation page and the refinements of D8 and D10.
- `docs/reference/semantic-anchors.md`: the rows "Clean Architecture" and "Vertical Slice Architecture (VSA)" link to ADR-0017.
  The anchor names stay exact; no anchor is added.
