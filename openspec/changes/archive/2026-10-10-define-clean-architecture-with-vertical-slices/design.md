# Design

## Context

The rebuild has six production modules (`voyager-api`, `voyager-physics`, `voyager-race`, `voyager-platform`,
`voyager-server`, `voyager-setup`) and a test module (`voyager-fitness`). `voyager-persistence` is still to come. See
`proposal.md` for why the rules are needed now. The greenfield design
(`docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`, decision D8 and the "Module architecture" section)
fixes the module graph. The DI decision D10, ADR-0016 and the avaje change (`switch-di-to-avaje-inject`, now applied)
fix the container. This design adds the layer rules, the slice rules and the package mapping on top of both.

Facts that shape the design, verified in the code on `main` (commit `21b1ee0`, 2026-10-10):

- The module graph matches the greenfield design. `voyager-api` depends on the JDK and `org.jetbrains` annotations only.
  `voyager-physics` and `voyager-race` depend on `api`. `voyager-platform` depends on `api`, `physics` and `race`.
  `voyager-setup` depends on `api` and `platform` only. `voyager-server` depends on all of them and uses avaje-inject
  (`ServerBeans`, `RaceBeans`, `BeanScope` in `VoyagerServer`). Guice is gone from the rebuild.
- Inside `race`, the packages are slice-shaped: `collision`, `effect`, `flow`, `line`, `progress`, `run`, `scoring`. The
  cup slice does not exist yet; its logic is in `voyager-server`.
- Inside `platform`, the feature packages are `catalog`, `collision`, `flight`, `hud`, `render`, and the packages `tick`
  and `world` mix infrastructure with slice classes. `convert` and `text` are infrastructure.
- `voyager-setup` exists with `api.mapsetup` (contract), `setup.mapsetup` (pure), `setup.adapter` (Minestom code),
  `setup.inject` (`SetupBeans`) and `SetupServer` (entry point, uses `BeanScope`).
- The slice graphs inside `race` and `platform` are not acyclic. `race` is acyclic: `run` to `flow`, `progress` and
  `scoring`, `scoring` to `progress`, `progress` to `collision`. `platform` has a cycle between the `catalog` and `world`
  packages (`world/MapInstances.java:3` imports `catalog.WorldOpener`; `catalog/CatalogValidation.java:8-9` imports
  `world.WorldFolders` and `world.WorldHealth`). Migration item 27.
- `voyager-server` still holds the cup use case. `server/game/CupSession.java` is 808 lines and computes map scores,
  standings and medal outlooks. The per-tick order is written in code, in `CupSession.tick()` (`CupSession.java:316-327`):
  flight sampling (`:317`), boost advance (`:322`), then the phase driver's `onUpdate()` (`:327`).
- `JsonMapCatalog` and `JsonCupCatalog` are gone (`unify-catalog-loading`). Catalogue reading is `CatalogLoader`, the
  played catalogue is held by `CatalogHolder`, and a reload goes through `CatalogReloader` and `CatalogReloadService`.
  `CupResolution` moved to `platform.catalog`.
- No mutable static field, no clock read (`System.currentTimeMillis`, `System.nanoTime`, `Instant.now`) and no DI
  annotation exists in `voyager-api`, `voyager-physics`, `voyager-race` or `voyager-platform` `src/main`. The DI
  annotations that exist are in the composition roots (`server.inject`, `VoyagerServer`, `setup.inject`, `SetupServer`).
- No `net.minestom`, `java.nio.file`, Xerus, Paper, Gson or Falco import exists in `voyager-api`, `voyager-physics` or
  `voyager-race` `src/main`, and no `platform` or `server` import exists in those modules.

## Goals / Non-Goals

**Goals:**
- One written target for the rings (Clean Architecture) and the slices (VSA), covering every current package.
- A slice-by-module package table that a contributor can use to place a new class.
- A DI rule per ring, consistent with `switch-di-to-avaje-inject` and ADR-0016.
- Each rule mapped to an existing or proposed ArchUnit rule (see `specs/architecture/architecture-enforcement`).
- A migration list with file and line evidence, so the follow-up changes start from facts.

**Non-Goals:**
- No code, package, or test moves in this change. Every move is a follow-up with its own type.
- No new fitness rules in this change. They belong to `test(fitness)` in `add-architecture-slice-rules`.
- No decision on the avaje bootstrap details. `server/dependency-injection` (switch-di change) owns them.
- No change to `voyager-setup`'s code. Its adapter placement is migration item 26.
- No change to the tree being replaced (`server` as a legacy module, `plugins/*`, `shared/*`).

## Decisions

### 1. Rings are Gradle modules (Clean Architecture)

Dependencies point inward, toward `voyager-api`. Four rings:

```
  ring 4  composition root   voyager-server          voyager-setup
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

Ring 3 and ring 4 are separate rings, but `voyager-platform` is one module for both adapters and frameworks, and
Minestom is meant to be confined to it. Today two composition-root modules import Minestom: `voyager-server` (its game
package, migration items 6 to 9; `VoyagerServer` also uses Minestom for the bootstrap, which is wiring) and `voyager-setup`
(`setup.adapter`, item 26). The module boundary is the enforced
line between ring 3 and the rest, and the items name the classes that cross it.

### 2. Slices are packages inside each module (VSA)

A slice is a feature column. It owns its use-case types, its ports and its adapters in every ring it touches. Its packages
are `net.elytrarace.voyager.<module>.<slice>..`. A module with exactly one slice uses its root package as that slice.
Here that is `voyager-physics`: its root package is the flight slice, and `collision`, `step` and `math` are technical
layers inside it.

Slice list (the nine current feature slices, `mapsetup` which now has a contract and a module, and one future slice):

| Slice | Feature | Notes |
|---|---|---|
| `flight` | Elytra flight simulation, collision space, firework boost, flight tracking | `physics` root in `voyager-physics` |
| `ring` | Ring passes, progress through rings, ring effects | Absorbs `race.collision`, `race.progress`, `race.effect` (owner decision, 2026-10-09) |
| `run` | One racer's run through a map | Orchestrates `flow`, `ring`, `scoring` |
| `flow` | Race phases, clock, timings | State machine is pure; Xerus driver is platform |
| `scoring` | Map and cup scores, medals, placement | Pure |
| `cup` | A cup: maps in order, standings, map transitions | New `race.cup`; today's logic is in `server.game` (owner decision, 2026-10-09) |
| `line` | Racing line through a map | Catmull-Rom path, renderer in platform |
| `catalog` | Map and cup definitions, their files and the played catalogue | Ports in api; JSON adapters and the holder in platform |
| `hud` | What a player sees in the race (boards, feedback, countdown) | Platform only |
| `mapsetup` | Creating and editing maps | Contract in `api.mapsetup`; use cases in `voyager-setup`; JSON store in platform (items 24, 26) |
| `record` (future) | Persisted runs and profiles | `voyager-persistence` (E5) |

### 3. Slice by module: the package table

Packages are the ones on `main` today. "Target" is where the class belongs after the follow-up moves in the migration
list. Cells with "-" mean the slice has no package in that module.

| Slice | `voyager-api` (ring 1) | `voyager-physics` (ring 1) | `voyager-race` (ring 2) | `voyager-platform` (ring 3) | `voyager-server` (ring 4) | `voyager-setup` (ring 4) |
|---|---|---|---|---|---|---|
| `flight` | `api.physics` (CollisionSpace, FlightInput, FlightState, exception). Target `api.flight`, which is optional and belongs to follow-up 4; `api.race.BoostConfig` and `InvalidBoostConfigException` also move there | `physics`, `physics.collision`, `physics.step`, `physics.math` | - | `platform.flight` (FlightTracker, FireworkBoostTracker, FlightTransition, FlightEndReason); `platform.collision` (MinestomCollisionSpace, BlockShapes, BlockCursor) and `platform.tick` (FlightTick, FlightTickDriver, FlightSample, FlightSampler, UntrackedFlightException): target `platform.flight` | `server.game` Rockets, LivePlayerSampler, Racers: target `platform.flight` | - |
| `ring` | `api.race` (Ring, RingType), `api.race.effect`, `api.race.exception` (InvalidRingException). Target `api.ring` | - | `race.collision` (RingPass), `race.progress` (ProgressTracker, ProgressUpdate, RingProgress), `race.effect` (RingEffectRegistry, SpeedMultiplierEffect). Target `race.ring` | - (`RingAdapter` lives in `platform.catalog.adapter`, owned by catalog) | - | - |
| `run` | - | - | `race.run` (RaceRun) | `platform.world.RaceRuns`: target `platform.run` | - | - |
| `flow` | `api.race.GameMode` (kernel) | - | `race.flow` (RaceStateMachine, RaceState, RacePhase, RaceClock, RaceTimings, exception) | `platform.tick` XerusPhaseDriver, RacePhaseListener: target `platform.flow` | `server.config.ServerSettings` reads RaceTimings (config, allowed) | - |
| `scoring` | `api.race` (MedalTier, MedalBrackets). Target `api.scoring` | - | `race.scoring` (MapScorer, MapScore, CupScorer, CupScore, MedalCountdown, MedalOutlook, Placement, PlacementBonus) | Consumers only: `platform.text.Messages`, `platform.hud.HudState` read MedalOutlook | `server.game` CupStandings, CupStanding: target `race.cup` | - |
| `cup` | `api.race` (CupDefinition, CupCatalog port), `api.race.exception.InvalidCupException`. Target `api.cup` | - | New `race.cup`: cup progression, standings, the logic of CupSession | `platform.catalog.CupResolution` (cup selection, item 23); `platform.world.MapTransition`: target `platform.cup` | `server.game` CupSession: target `race.cup`. The wiring that stays is `VoyagerServer` | - |
| `line` | `api.race` (GuideLine, GuidePoint), `api.race.exception` (InvalidGuideLineException). Target `api.line` | - | `race.line` (RacingLine, CatmullRom) | `platform.render.GuideLineRenderer`: target `platform.line` | - | - |
| `catalog` | `api.race` (MapCatalog, MapDefinition), `api.race.exception` (InvalidMapException). Target `api.catalog` | - | - | `platform.catalog` (CatalogLoader, CatalogReading, CatalogSnapshot, CatalogValidation, CatalogConsistency, CatalogDirectory, CatalogHolder, CatalogReloader, LoadedCatalog, ReloadOutcome, WorldOpener, adapter/, writer/, exception/). `JsonMapCatalog` and `JsonCupCatalog` are gone | `server.game.CatalogReloadService` (wiring, item 22); `server.config.ConfigCheck` (composition check, reads through platform) | - |
| `hud` | - | - | - | `platform.hud` (HudState, RaceHud, RaceFeedback, StartCountdown) | - | - |
| `mapsetup` | `api.mapsetup` (DraftStore, MapDraft, MapId, exception/). Target: unchanged | - | - | `platform.catalog.JsonDraftStore` and `platform.catalog.writer.MapDraftJsonWriter` (item 24); `platform.text.SetupMessages` (item 25) | - | `setup.mapsetup` (DraftEditor, MapStatus, RingDefaults, RingFromPose, RingOrientation, RingPicker, exception/): pure. `setup.adapter` (BuilderSessions, MapSession, RingPreviews, SetupCommands, TerrainGuard, Wand, WandListener): Minestom, item 26 |
| Kernel | `api.math` (Vec3, Aabb, exception); `api.race` root holds `GameMode` | - | - | - | - | - |
| Infrastructure | `api.config` (ConfigProblem; `config` is infrastructure, see Decision 4) | - | `race` root (RaceCore marker) | `platform` root (PlatformCore); `platform.text`; `platform.convert` (Vectors, VelocityExit); `platform.world` (MapInstances, WorldHealth, WorldFolders, VoidWorldTemplate); `platform.tick` (driver base) | `server` (VoyagerServer); `server.command` (RaceCommand, ReloadPermission); `server.config` (ServerSettings); `server.inject` (ServerBeans, RaceBeans) | `setup` (SetupServer); `setup.config` (SetupSettings); `setup.inject` (SetupBeans) |
| `record` (future) | - | - | - | - | - | `persistence` root and infrastructure in `voyager-persistence` |

Notes on the table:
- `api.physics` is a module-named package that holds the flight slice's contract. Its rename to `api.flight` belongs to
  follow-up 4 and is optional, and it is not done by default (see Open Questions). The rule in the spec is about the
  slice, not the name.
- `GameMode` is the only type in `api.race` root that stays there after the split. It is the kernel discriminator that
  four slices share.
- The `setup` column holds `voyager-setup`, the second composition root. Its `setup.adapter` is not a composition root
  package and is listed as a violation, not as a home for adapters.

### 4. Technical packages: slice or infrastructure

Rule: a package named for a feature is a slice; a package named for a technical role is infrastructure and may serve
several slices but owns no use-case type.

| Package | Classification | Decision |
|---|---|---|
| `platform.render` | Technical name, one feature inside | Not infrastructure. Its only class renders the racing line: move into `platform.line` (follow-up 3) |
| `platform.text` | Infrastructure (message bundles, palette, time format) | Stays. Slice-specific keys stay in the file that uses them. `SetupMessages` belongs to mapsetup and moves (item 25). It is consumed by `hud` and `server` |
| `platform.hud` | Slice `hud` | Stays |
| `platform.tick` | Infrastructure (the tick driver base) | Stays as infrastructure. Its flight classes move to `platform.flight` and its phase classes to `platform.flow` (follow-up 3) |
| `platform.world` | Infrastructure (instance and world health) | Stays for MapInstances, WorldHealth, WorldFolders and VoidWorldTemplate. RaceRuns moves to `platform.run` and MapTransition to `platform.cup` (follow-up 3). `WorldOpener` moves here to break the cycle (item 27) |
| `platform.convert` | Infrastructure (domain to Minestom value conversion) | Stays. It is the only place that converts values |
| `platform.collision` | Slice `flight` (CollisionSpace adapter) | Moves into `platform.flight` (follow-up 3) |
| `platform.catalog` | Slice `catalog`. Holds two classes of other slices (items 23, 24) | Stays for `catalog`. `CupResolution` (cup selection) and the mapsetup JSON store move out (follow-ups 2 and 3) |
| `platform.flight` | Slice `flight` | Stays |
| `server.game` | Not a slice and not infrastructure: a mix of cup use case and Minestom adapters | Dissolved by follow-up 2 into `race.cup`, `platform.flight`, `platform.world`. `CatalogReloadService` moves to `server` wiring with it (item 22) |
| `server.command` | Infrastructure (inbound command adapter) | Stays. Each command calls a use case in a slice |
| `server.config` | Infrastructure (configuration) | Stays. `ConfigCheck` is a composition-root check (item 22 note) |
| `server.inject` | Infrastructure (composition) | Stays. Holds the factories described in Decision 7 |
| `setup.adapter` | Ring 3 code in a ring 4 module (Minestom) | Not allowed where it is. Moves to `platform.mapsetup` (item 26, proposed follow-up 5) |
| `setup.mapsetup` | Slice `mapsetup`, pure | Stays |
| `api.config` | Infrastructure (the configuration problem report, shared by `setup` and `server`) | Stays in `voyager-api`. The spec names `config` as infrastructure, and a report type that two modules use is declared in api |

### 5. Ports, data and exceptions per ring

**Ports.** A port is an interface that an inner ring calls and an outer ring implements. It is declared in the innermost
module that calls it. It is declared in `voyager-api` when more than one module calls it. Examples: `CollisionSpace`
(called by physics, implemented by platform) lives in `api.physics`. `CupCatalog` and `MapCatalog` (called by race and
platform, implemented by platform) live in `api.race` today, with targets `api.cup` and `api.catalog`. A port never lives
in the ring that implements it. `DraftStore` (called by `setup`, implemented by `platform.catalog.JsonDraftStore`) lives
in `api.mapsetup`.

**Data.** Data crosses a boundary as an immutable record or enum. The records of `voyager-api` are the transfer format
between rings. They carry no Minestom, Adventure, Gson, Falco or JDBC type. A conversion from a platform value to a domain
value happens in `voyager-platform` only (`Vectors` for `Vec` to `Vec3`, `VelocityExit` for the velocity write). Domain
records validate in their compact constructor (`Vec3` rejects NaN and infinity). `LoadedCatalog` and `CatalogSnapshot` are
records of the platform that the server holds, and the server reads them through `CatalogHolder`.

**Exceptions per ring.** Each exception sits in an `exception` subpackage next to the code that raises it, with a
`package-info.java`, extends `RuntimeException`, and ends with `Exception`:

| Ring | Where the exception lives | Example today |
|---|---|---|
| 1 entities | `api.<slice>.exception`, `api.math.exception`, `physics.exception` | `api.math.exception.NonFiniteVectorException`, `physics.exception.InvalidSimulationStateException` |
| 2 use cases | `race.<slice>.exception` | `race.flow.exception.IllegalPhaseTransitionException` |
| 3 adapters | `platform.<slice or infra>.exception` | `platform.catalog.exception.MalformedCatalogFileException` |
| 4 composition | `server.<area>.exception`, `setup.<area>.exception` | `server.config.exception.MissingServerDirectoryException`, `setup.mapsetup.exception.InvalidPoseException` |

An adapter translates external failures (I/O, Minestom, Gson, Falco) into exceptions of its own ring before they reach
an inner ring. Inner rings never catch an outer exception. The cup selection exception `UnresolvedCupException` is a
use-case exception that is raised in ring 3 today (`platform.catalog.exception`, item 5 and item 23).

### 6. Cross-slice communication and the shared kernel

- **Calls.** A slice calls another slice through the other slice's public types: its root package and its `exception`
  package. A helper that must stay hidden lives in `<slice>.internal`, and no class outside the slice imports it.
- **Direction.** The slice graph inside a module is a DAG. Inside `race`, today's edges are `run` to `flow`, `progress`
  (part of `ring`) and `scoring`, and `scoring` to `progress`. `ring` depends on nothing else in race. Progress is part of
  `ring`, which keeps the graph acyclic: if `progress` were a separate `run` package, `run -> scoring -> progress -> ...`
  would form a cycle through `RaceRun` (`race/run/RaceRun.java:10`, `race/scoring/MapScorer.java:7`) as soon as `progress`
  depended on `run`.
- **Infrastructure counts.** The cycle rule covers every package of a module, infrastructure packages included. A cycle
  between a slice and an infrastructure package is a cycle (item 27).
- **Events.** Inside a tick, slices talk through method calls that the composition root sequences. There is no shared
  mutable state between slices.
- **Shared kernel.** `api.math` (vectors and boxes) and the `GameMode` discriminator. Kernel types are immutable and
  platform-free. A change to the kernel needs an ADR, because every slice sees it.

### 7. Dependency injection (avaje-inject and jakarta.inject)

| Ring | Annotations allowed | Where |
|---|---|---|
| 1 entities | None | `voyager-api`, `voyager-physics` |
| 2 use cases | None. Classes take constructor parameters and nothing else | `voyager-race` |
| 3 adapters | None. Classes are constructed by `@Bean` methods of the composition root | `voyager-platform`; later `voyager-persistence`. No `@Inject`, `@Singleton`, `@Named` or avaje type |
| 4 composition | `jakarta.inject`, avaje `@Factory`, `@Bean`, `@External`, `BeanScope` | `voyager-server` `inject` package and `VoyagerServer`; `voyager-setup` `inject` package and `SetupServer` |

Spike facts from `switch-di-to-avaje-inject` (task 1, passed 2026-10-09) that bind this table:
- Avaje wires a class only when it carries `@Singleton` (or `@Component`) and is compiled in the composition root.
  `@Inject` alone is not picked up. A class from another module is wired only by a `@Bean` method, which is why ring 3
  needs no annotation at all.
- A missing bean fails `compileJava` and names the type. An ambiguous bean fails too, but the message names the
  conflicting `@Bean` methods, not the type.

Rules that follow from the table:
- **One composition root per deployable.** `voyager-server` for the game server, `voyager-setup` for the setup server.
  They do not share a root, and `voyager-setup` does not import `voyager-server`.
- **One factory per slice group.** The server's `inject` package holds `ServerBeans` and `RaceBeans`, which exist now.
  The follow-up splits them into `@Factory` classes named for slices, for example `RunBeans`, `CupBeans`, `FlightBeans`,
  and it is a refactor of those two classes, not a new design. `SetupBeans` is the setup server's one factory.
- **Ordered pipeline.** The per-tick steps are one unmodifiable `List`, built in one `@Bean` method and passed to the
  tick. The order today is the order in `CupSession.tick()`: flight sampling (`CupSession.java:317`), boost advance
  (`:322`), phase advance (`:327`). A test asserts that order. A collection injection of the steps is not used, because
  the order of collection injection is not guaranteed. No such `@Bean` method exists yet (item 10).
- **Tests.** Unit tests construct with `new`. Composition-root tests build a fresh `BeanScope` per test on a `@TempDir`.
  No static field holds a scope.
- **No service locator.** Only composition-root code may call a container. A class that calls a container lookup is a
  service locator, and the rule forbids it.
- **No static singletons.** A process-wide object is one instance created by the composition root, passed to its users.
  `CatalogHolder` is one example: the server creates it and passes it to the cup session and the reload service. Static
  methods are allowed only as pure functions or as factories of an abstract utility class with a private constructor
  (ManisGame rule 2).

**Alternatives considered.** Keep Guice: rejected in the switch-di change (D10). Manual wiring in one method: viable, and
the spec is written so that it would satisfy the same rules. Dagger: rejected in the switch-di change. Let each slice own
its own container: rejected because it puts container types inside the slice rings.

### 8. Enforcement mapping

Existing rules named in `voyager-fitness`, all verified against the suite on 2026-10-10 (`ApiPurityTest` unless marked):

| Requirement | Existing rule |
|---|---|
| Entities and use cases have no framework types | `apiDoesNotDependOnMinestomOrItsWorldLoader`, `apiDoesNotDependOnPaper`, `apiDoesNotDependOnXerus`, `apiDoesNotDependOnPersistenceTechnology`, `apiDoesNotPerformFileIo`, `physicsDoesNotDependOnMinestomOrItsWorldLoader`, `physicsDoesNotDependOnXerus`, `physicsDoesNotPerformFileIo`, `raceDoesNotDependOnMinestomXerusOrItsWorldLoader`, `raceDoesNotDependOnPaper`, `raceDoesNotDependOnPersistenceTechnology` |
| Dependencies point inward (partial) | `apiDoesNotDependOnPlatform`, `physicsDoesNotDependOnRaceOrPlatform`, `raceDoesNotDependOnPlatform`, `platformDoesNotDependOnServer` |
| Platform types confined to converters | `onlyBlockShapesReachesForMinestomsShapeImplementation`, `onlyVelocityExitSendsAVelocityToMinestom`, `onlyVectorsBuildsAMinestomVectorFromDomainCoordinates`, `onlyPlatformDependsOnGson`, `onlyTheTextPackageBuildsAUserFacingString` |
| DI boundary (partial) | `apiDoesNotDependOnADiContainer`, `physicsDoesNotDependOnADiContainer`, `onlyServerDependsOnDiContainer` (avaje after switch-di; `setup` is not covered by this rule and is named in the proposed rules below) |
| Domain values and exceptions | `DesignRuleTest`: `apiTypesAreRecordsInterfacesOrEnums`, `exceptionsAreUncheckedAndDomainNamed`, `exceptionsLiveInAnExceptionSubpackage`, `race_domain_exceptions_are_runtime_exceptions` |
| Package-level annotations | `NullabilityConventionTest` |
| No vacuous pass, every module covered | `FitnessCoverageTest` |

Proposed rules for `add-architecture-slice-rules` (each with `allowEmptyShould(false)`):

| Requirement | Proposed rule |
|---|---|
| Ring order | `layeredArchitecture()` with layers Entities (`..api..`, `..physics..`), UseCases (`..race..`), Adapters (`..platform..`, `..persistence..`), Composition (`..server..`, `..setup..`) |
| Slice cycles | `slices().matching("net.elytrarace.voyager.race.(*)..").should().beFreeOfCycles()`; `slices().matching("net.elytrarace.voyager.platform.(*)..")` over every package directly under the platform root, infrastructure included (item 27 is caught only this way); the same for `api.(*)` |
| Slice internals | Generated per slice: `noClasses().that().resideOutsideOfPackage(<slice>..).should().dependOnClassesThat().resideInAPackage(<slice>.internal..)` |
| DI placement | `io.avaje.inject..` and `jakarta.inject..` forbidden outside `..server..` and `..setup..`; no DI annotation on any platform class; `BeanScope` only in `..server.inject..`, `VoyagerServer`, `..setup.inject..` and `SetupServer` |
| Minestom stays out of setup | `noClasses().that().resideInAPackage("..setup..").should().dependOnClassesThat().resideInAPackage("net.minestom..")`; held back until item 26 is fixed |
| Minestom adapters out of the server's game package | `noClasses().that().resideInAPackage("..server.game..").should().dependOnClassesThat().resideInAPackage("net.minestom..")`; held back until items 6 to 9 are fixed. `VoyagerServer` bootstrap is outside this package and stays allowed |
| No mutable statics, no clock reads | Non-final static field rule for all modules; `callMethod` rules for `System.currentTimeMillis`, `System.nanoTime` and `Instant.now` in api, physics and race |
| Server holds no scoring after the move | After follow-up 2: `noClasses().that().resideInAPackage("..server..").should().dependOnClassesThat().resideInAPackage("..race.scoring..")` |

ArchUnit calls named above must be checked against ArchUnit 1.5.0 (the version in `settings.gradle.kts`, not 1.4.2 as
an earlier draft said) during apply. Where the exact method is not in that version, the rule uses a predicate that is.

## Current violations (migration list)

These are findings in `main` on 2026-10-10. Each line is a candidate for one of the follow-up changes. None is fixed
here. Numbers 1 to 21 are the original list, kept for traceability; items 22 to 27 were found when the list was
re-checked against the current code. Status: **Open** means a follow-up must change the code; **Resolved** means the
code no longer does what the item describes; **Acceptable** means the item is listed for completeness and needs no change.

**Composition root holds use-case logic and platform adapters (ring 4):**
1. **Open.** `voyager-server/src/main/java/net/elytrarace/voyager/server/game/CupSession.java:33-37` imports `race.scoring`
   (`CupScore`, `MapScore`, `MapScorer`, `MedalCountdown`, `MedalOutlook`), and the cup use case is computed there:
   `MapScorer.score` at `:523`, `standings.scoreOn` at `:645`, `MedalCountdown.outlook` at `:723`. The file is 808 lines.
   Target `race.cup`, with wiring left in `VoyagerServer`. Follow-up 2.
2. **Open.** `voyager-server/src/main/java/net/elytrarace/voyager/server/game/CupStandings.java:4-8` imports five `race.scoring`
   types, and uses `GameMode` (`:3`) to compute standings. Target `race.cup`. Follow-up 2.
3. **Open.** `voyager-server/src/main/java/net/elytrarace/voyager/server/game/CupStanding.java:3` imports `race.scoring.CupScore`.
   Target `race.cup`. Follow-up 2.
4. **Resolved.** The server no longer depends on a JSON catalogue class. `JsonCupCatalog` is gone, and `CupResolution`
   is in `platform.catalog` and takes a `CatalogReading`. The remaining question, cup selection in ring 3, is item 23.
5. **Open, location moved.** `UnresolvedCupException` is now `voyager-platform/src/main/java/net/elytrarace/voyager/platform/catalog/exception/UnresolvedCupException.java`.
   The `server/game/exception` package holds only `package-info.java`. It is a use-case exception and belongs with
   `race.cup`. Follow-up 2, together with item 23.
6. **Open.** `voyager-server/src/main/java/net/elytrarace/voyager/server/game/Racers.java:6-12` uses Minestom players and
   `platform.convert`. Target `platform.flight`. Follow-up 2.
7. **Open.** `voyager-server/src/main/java/net/elytrarace/voyager/server/game/Rockets.java:4-14` uses Minestom firework
   entities. Target `platform.flight`. Follow-up 2.
8. **Open.** `voyager-server/src/main/java/net/elytrarace/voyager/server/game/LivePlayerSampler.java:5-8` uses `platform.flight`
   and `platform.tick`, and Minestom (`:9-11`). Target `platform.flight`. Follow-up 2.
9. **Open.** `voyager-server/src/main/java/net/elytrarace/voyager/server/game/CurrentMapBlocks.java:3-5` uses Minestom instances
   and blocks. Target `platform.world`. Follow-up 2.
10. **Open.** `voyager-server/src/main/java/net/elytrarace/voyager/server/game/CupSession.java:316-327` defines the tick order in
    the body of `tick()`. Target: declare the per-tick step order once in one `@Bean` method and test it, in follow-up 2.
    The switch-di change no longer covers this (moved 2026-10-09).

**Technical packages with slice content (ring 3):**
11. **Open.** `voyager-platform/src/main/java/net/elytrarace/voyager/platform/world/RaceRuns.java:6-7` imports `race.flow.RaceClock`
    and `race.run.RaceRun`. Target `platform.run`. Follow-up 3.
12. **Open.** `voyager-platform/src/main/java/net/elytrarace/voyager/platform/world/MapTransition.java` is cup behaviour in the
    world package (it moves players between maps of a cup). Target `platform.cup`. Follow-up 3.
13. **Open.** `voyager-platform/src/main/java/net/elytrarace/voyager/platform/tick/XerusPhaseDriver.java:4-8` and
    `platform/tick/RacePhaseListener.java:3` hold the flow slice. Target `platform.flow`. Follow-up 3.
14. **Open.** `voyager-platform/src/main/java/net/elytrarace/voyager/platform/tick/FlightTick.java:5` and
    `platform/tick/FlightTickDriver.java:6-8` hold the flight slice. Target `platform.flight`. Follow-up 3.
15. **Open.** `voyager-platform/src/main/java/net/elytrarace/voyager/platform/render/GuideLineRenderer.java:6` imports `race.line`
    from a `render` package. Target `platform.line`. Follow-up 3.
16. **Open.** `voyager-platform/src/main/java/net/elytrarace/voyager/platform/collision/MinestomCollisionSpace.java` is the flight
    slice's `CollisionSpace` adapter in its own package. Target `platform.flight`. Follow-up 3.

**Slice split across packages (ring 2):**
17. **Open.** `voyager-race/src/main/java/net/elytrarace/voyager/race/collision/RingPass.java`,
    `race/progress/ProgressTracker.java:6` (imports `race.collision.RingPass`), and `race/effect/` are one slice, `ring`,
    in three packages. Target `race.ring`. Follow-up 3 (or the rename is part of the next `race` change).

**Flat contract package (ring 1):**
18. **Open.** `voyager-api/src/main/java/net/elytrarace/voyager/api/race/` holds 12 types from six slices plus the kernel:
    `Ring`, `RingType`, `MapDefinition`, `MapCatalog`, `CupDefinition`, `CupCatalog`, `GuideLine`, `GuidePoint`,
    `MedalTier`, `MedalBrackets`, `BoostConfig`, `GameMode`. Target `api.<slice>` and `api.race` root with `GameMode` only.
    Follow-up 4. `voyager-api/src/main/java/net/elytrarace/voyager/api/physics/` keeps its name by default (Open Questions);
    a rename to `api.flight` is optional in the same follow-up.

**Acceptable as they are, listed for completeness:**
19. **Acceptable.** `voyager-race/src/main/java/net/elytrarace/voyager/race/run/RaceRun.java:6-10` imports `flow` (`:6`),
    `progress` (`:7-9`) and `scoring` (`:10`). This is the run slice composing the others through their public types. It is
    allowed by Decision 6 and needs no change.
20. **Acceptable.** `voyager-platform/src/main/java/net/elytrarace/voyager/platform/text/Messages.java:4` and
    `platform/hud/HudState.java:3` import `race.scoring.MedalOutlook`. Adapters may depend on the inner ring. No change.
21. **Resolved.** `voyager-server/src/main/java/net/elytrarace/voyager/server/inject/VoyagerModule.java` is gone, and
    `VoyagerServer` uses avaje's `BeanScope` (`VoyagerServer.java:3`). No Guice import remains in `voyager-server`.
    The factories are `ServerBeans` and `RaceBeans`.

**Found on 2026-10-10 (re-check against the current code):**
22. **Acceptable, package to change.** `voyager-server/src/main/java/net/elytrarace/voyager/server/game/CatalogReloadService.java`
    sequences one operator reload off the tick thread and replies with messages. It holds no catalogue rule (those are in
    `CatalogReloader`). Its package `server.game` is dissolved by follow-up 2, and the class moves to `server` wiring
    with it. The composition-root check `server/config/ConfigCheck.java` is acceptable for the same reason: it asks the
    same questions as `ServerSettings` and the catalogue loader, and it computes no score or standing.
23. **Open.** `voyager-platform/src/main/java/net/elytrarace/voyager/platform/catalog/CupResolution.java` holds cup-selection
    rules (a named cup resolves to that cup, an unnamed selection needs exactly one cup file, duplicates are refused). These
    are cup use-case rules in ring 3. Target: the selection rule over parsed `CupDefinition`s moves to `race.cup`; reading
    the files stays in `platform.catalog`. The `CatalogReading` parameter is a platform type, which is why the rule is not
    yet a pure function. Follow-up 2.
24. **Open, classification.** `voyager-platform/src/main/java/net/elytrarace/voyager/platform/catalog/JsonDraftStore.java` and
    `platform/catalog/writer/MapDraftJsonWriter.java` implement `api.mapsetup.DraftStore` and belong to the mapsetup slice,
    not to `catalog`. Ring 3 is correct; the package is not. Target `platform.mapsetup`. Follow-up 3.
25. **Open, classification.** `voyager-platform/src/main/java/net/elytrarace/voyager/platform/text/SetupMessages.java` holds the
    message keys of the mapsetup slice in the infrastructure `text` package. Target `platform.mapsetup`. Follow-up 3.
26. **Open, ring violation.** `voyager-setup/src/main/java/net/elytrarace/voyager/setup/adapter/` (`BuilderSessions`, `MapSession`,
    `RingPreviews`, `SetupCommands`, `TerrainGuard`, `Wand`, `WandListener`) imports `net.minestom`. Platform code sits in
    a composition-root module, which breaks the rule that Minestom is confined to `voyager-platform`, and the layered rule
    cannot see it, because `..setup..` is one layer. Target `platform.mapsetup`, proposed follow-up 5
    (`regroup-setup-adapters`, needs owner approval). The "Minestom stays out of setup" rule holds back until then.
27. **Open, cycle.** `voyager-platform/src/main/java/net/elytrarace/voyager/platform/world/MapInstances.java:3` imports
    `platform.catalog.WorldOpener`, and `platform/catalog/CatalogValidation.java:8-9` imports `platform.world.WorldFolders`
    and `platform.world.WorldHealth`. The `catalog` and `world` packages depend on each other. `world` is infrastructure,
    so the cycle rule as first drafted would not flag it; the proposed rule now covers every platform package. Target:
    `WorldOpener` moves into `platform.world`, which removes the back edge. Follow-up 3.

**Counts (2026-10-10):** 27 items. Open: 22 (17 of the original 18 violations, which are items 1 to 3, 5 to 18; and items
23 to 27). Resolved: 2 (items 4 and 21). Acceptable: 3 (items 19, 20 and 22).

**Checked and clean:**
- No Minestom, Xerus, Paper, Gson, Falco, `jakarta.inject`, `com.google.inject`, `io.airlift`, `io.avaje` or `java.nio.file`
  import in `voyager-api`, `voyager-physics` or `voyager-race` `src/main`. No `platform`, `server` or `setup` import in those modules.
- No `platform` import of `server` or `setup`.
- No mutable static field and no clock read in `voyager-api`, `voyager-physics`, `voyager-race` or `voyager-platform`
  `src/main`.
- No cycle between slices of `race`. Inside `platform`, the feature slices have no cycle; the cycle in item 27 runs
  through infrastructure.

## Risks / Trade-offs

- **[The table and the code drift apart] -> Each follow-up updates the table in this file and in `docs/explanation/architecture.md` in the same PR.** The ArchUnit slice rules in follow-up 1 turn the table into a check for cycles and internals.
- **[Folding progress into ring makes ring bigger] -> Accepted.** Ring-pass tracking, progress and effects change together; a separate package would only add an edge that a review must justify.
- **[Rules added before the moves go red] -> The requirement "Rules enter the suite only after the violations they flag are fixed" in `architecture-enforcement`.** Follow-ups 2 and 3 precede the rules that they would break.
- **[The composition root's tick order is wiring, but a reviewer may read it as logic] -> The spec names the order as wiring, and the test asserts it.** Only the order is in the root; each step's logic stays in its slice.
- **[Ring 3 and ring 4 share one module] -> The module boundary is the enforced line for platform. Setup and server import Minestom today (items 6 to 9 and 26); those are migrations, not exceptions.**
- **[A slice split later would move many imports] -> Follow-up 4 is one `refactor(api)` change, done before more code depends on `api.race`.**
- **[The ArchUnit method names in Decision 8 may differ in the pinned version] -> Verify during apply against 1.5.0; the requirement stays the same.**
- **[Two composition roots drift apart] -> Each root has one factory group, and `voyager-setup` never imports `voyager-server` (`architecture-enforcement`).**

## Migration Plan

This change ships documentation only. The order of the work that follows:

1. This change: specs, design, ADR-0017, explanation page, pointers. Merged locally as the `docs(*)` commits named in
   `tasks.md`. No behaviour changes.
2. Follow-up 1, `add-architecture-slice-rules` (`test(fitness)`): adds the proposed rules whose violations are already fixed, and records the rest as held back. The slice-cycle rule for platform covers infrastructure, so item 27 holds it back.
3. Follow-up 2, `move-cup-flow-out-of-server` (`refactor(server)`): items 1 to 10, 22 and 23. The cup-slice refactor owns "declare the per-tick step order once and test it" (item 10). That work moved out of `switch-di-to-avaje-inject` on 2026-10-09, because `CupSession.tick()` is not a list of uniform systems and turning it into one is a redesign.
4. Follow-up 3, `regroup-platform-by-slice` (`refactor(platform)`): items 11 to 17, 24, 25 and 27.
5. Follow-up 4, `flatten-api-by-slice` (`refactor(api)`): item 18. A rename of `api.physics` to `api.flight` is part of it only if the owner revises the default.
6. Proposed follow-up 5, `regroup-setup-adapters` (`refactor(setup)` or `refactor(platform)`, owner to choose): item 26. Needs the owner's approval before it starts.
7. The avaje change is applied. Follow-up 2 splits `ServerBeans` and `RaceBeans` into per-slice factories (items 9 and 10 touch the same classes).

Rollback: each follow-up is one squash commit on `main` and reverts cleanly. This change has no code to revert. ADR-0017
is Accepted by the owner's instruction of 2026-10-10; a later change to it is a new ADR that supersedes it.

## Open Questions

Deferrable: none of these changes this change's specs or tasks. Each is decided in the follow-up that needs it.

- Whether `RaceRun` should depend on `ring` through a port, if `run` and `ring` need to be separated later (follow-up 3).
- Whether `voyager-setup`'s composition root shares a `@Factory` base type with `voyager-server` (greenfield open question; deferred).
- Whether `setup.adapter` moves to `voyager-platform` (item 26, the recommended answer) or to a separate ring-3 module. The
  recommended answer keeps the D8 module cut.

Human checkpoints, decided by default on 2026-10-10 (owner may revise). The owner was not asked; the defaults below are
what the design recommended.
- **Slice names `catalog` and `hud`:** kept. `catalog` covers the map and cup definitions, their files and the played
  catalogue. `hud` covers what a player sees during a race.
- **Rename `api.physics` to `api.flight`:** not done now. `api.physics` keeps its name. A rename, if the owner wants it,
  belongs to follow-up 4 (`flatten-api-by-slice`).

Decided by the owner on 2026-10-09 (recorded in ADR-0017):
- The cup flow (`CupSession`, `CupStandings`, `CupResolution`) moves from `server/game` into a new `race.cup` slice;
  `voyager-server` keeps only wiring. `CupResolution` is now in `platform.catalog`; item 23 records the move.
- `progress` folds into the `ring` slice together with `collision` and `effect`.
- Order: `switch-di-to-avaje-inject` is applied first; this change follows. Applied on `main`.

## Documentation

- `docs/decisions/0017-clean-architecture-with-vertical-slices.md` (MADR 4.0, Accepted, 2026-10-10). Number 0017 is
  reserved for this change. 0016, 0018 and 0019 are on `main`; 0012 to 0015 are on the unmerged
  `refactor/architecture-ratchet` branch. The ADR cites ADR-0016 (`switch-di-to-avaje-inject`) and greenfield D8 and D10.
- `docs/explanation/architecture.md` (Diataxis "explanation"; the folder is new). It explains the rings, the slices, the
  table, the DI rules and the enforcement mapping for a reader who wants the reasoning, and points to the spec for the
  normative statements. Its `catalog` row is updated by `unify-catalog-loading` task 8.1.
- `CLAUDE.md`, section "Architecture": one pointer line to the explanation page and ADR-0017. The ECS, Elytra and Data Flow subsections describe the tree being replaced and are kept. The rebuild's text in that section is none, so nothing is replaced.
- `docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`: a cross-reference in "Module architecture" to ADR-0017,
  the explanation page and the refinements of D8 and D10. "ECS at the tick layer only" is not changed.
- `docs/reference/semantic-anchors.md`: the rows "Clean Architecture" and "Vertical Slice Architecture (VSA)" link to
  ADR-0017. The anchor names stay exact; no anchor is added.
- `docs/research/005-simpler-map-and-cup-setup.md`: roadmap Q1 is marked implemented by `unify-catalog-loading`.
