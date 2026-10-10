# architecture/vertical-slices Specification

## Purpose
Defines the "Vertical Slice Architecture (VSA)" of the Voyager rebuild, which the project also calls its column
architecture: each feature is a slice that runs through the rings and owns its types end to end. It fixes the package
mapping per module, how slices may talk to each other, the shared kernel, and which technical packages count as
infrastructure rather than as slices.

## Requirements

### Requirement: Features are slices that own their types end to end
**Priority:** MoSCoW Must

THE SYSTEM SHALL organise behaviour by feature. A slice SHALL own the use-case types, ports and adapters for its
feature in every ring it touches. The slices are `flight`, `ring`, `run`, `flow`, `scoring`, `cup`, `line`, `catalog`,
`hud` and `mapsetup` (its contract in `api.mapsetup`, its use cases in `voyager-setup`), and the future slice `record`
(in `voyager-persistence`).

#### Scenario: New feature gets its own slice
- **WHEN** a feature such as a race leaderboard is added
- **THEN** its use-case types, ports and adapters sit in one slice per module, not spread across technical packages

#### Scenario: Behaviour owned by two slices
- **WHEN** a type is needed by two slices and is not part of the shared kernel
- **THEN** it is declared by the slice that owns the concept, and the other slice depends on it through that slice's public types

### Requirement: Slice packages follow the module-and-slice pattern
**Priority:** MoSCoW Must

THE SYSTEM SHALL place each slice's packages under `net.elytrarace.voyager.<module>.<slice>..`. WHEN a module contains
exactly one slice, THE SYSTEM SHALL use the module root package as that slice, and the subpackages of the module root
SHALL be technical layers within that one slice.

#### Scenario: Run slice in the use-case module
- **WHEN** the run use case is placed in `voyager-race`
- **THEN** its types live under `net.elytrarace.voyager.race.run..`

#### Scenario: Flight simulation in the physics module
- **WHEN** elytra flight simulation code is placed in `voyager-physics`
- **THEN** it lives under `net.elytrarace.voyager.physics..`, which is the flight slice, and its `collision`, `step` and `math` subpackages are technical layers inside that slice

### Requirement: Slices expose contracts in api under their own name
**Priority:** MoSCoW Must

WHEN a type of a slice is used by another module, THE SYSTEM SHALL declare it in `net.elytrarace.voyager.api.<slice>..`.
WHEN a type is used only inside its own module, THE SYSTEM SHALL keep it in that module's slice package and out of
`voyager-api`.

#### Scenario: Map definition read by platform and race
- **WHEN** the map definition is used by both `voyager-race` and `voyager-platform`
- **THEN** it is declared under `net.elytrarace.voyager.api.catalog..`

#### Scenario: Scoring helper used only in race
- **WHEN** a helper class is used only by `voyager-race`
- **THEN** it does not appear in `voyager-api`

### Requirement: Cross-slice access goes through public types only
**Priority:** MoSCoW Must

THE SYSTEM SHALL allow a slice to depend on another slice only through that slice's root package and its `exception`
subpackage. A slice SHALL NOT depend on another slice's `internal` subpackage, and no class outside a slice SHALL import
from that slice's `internal` subpackage.

#### Scenario: Scoring reads ring progress through its public type
- **WHEN** the scoring slice uses `RingProgress` from the ring slice's root package
- **THEN** the dependency is allowed

#### Scenario: Scoring reaches into run internals
- **WHEN** the scoring slice imports a class from `net.elytrarace.voyager.race.run.internal..`
- **THEN** the architecture rules fail the build

### Requirement: The slice graph inside each module is acyclic
**Priority:** MoSCoW Must

THE SYSTEM SHALL keep the dependency graph between slices of one module free of cycles. A slice that needs a
type from a slice that already depends on it SHALL move the type into the kernel or into a slice that both can depend on.
The rule covers infrastructure packages as well: a cycle between a slice and an infrastructure package, or between two
infrastructure packages, is a cycle.

#### Scenario: Two slices depend on each other
- **WHEN** the run slice depends on scoring and scoring depends on run
- **THEN** the architecture rules report a slice cycle and the build fails

#### Scenario: Slice and infrastructure package depend on each other
- **WHEN** the `catalog` package of `voyager-platform` depends on the `world` package and `world` depends on `catalog`
- **THEN** the architecture rules report the cycle and name both packages; today this cycle exists (migration item 27)

### Requirement: Shared kernel holds only pure values
**Priority:** MoSCoW Must

THE SYSTEM SHALL keep the shared kernel to `net.elytrarace.voyager.api.math..` (vectors and boxes) and to the `GameMode`
discriminator in `net.elytrarace.voyager.api.race`. Kernel types SHALL be immutable values with no platform or framework
type, and a change to the kernel SHALL be recorded in an ADR.

#### Scenario: Platform type added to the kernel
- **WHEN** a Minestom type is added to `net.elytrarace.voyager.api.math..`
- **THEN** the architecture rules fail the build

#### Scenario: New kernel type
- **WHEN** a new type is proposed for the kernel
- **THEN** the change includes an ADR that names every slice that needs it

### Requirement: Technical packages are infrastructure, not slices
**Priority:** MoSCoW Must

THE SYSTEM SHALL classify packages named for a technical role (`text` for message handling, `convert` for value
conversion, `world` for instance management, `tick` for the tick adapter, `config`, `command`, `inject`) as
infrastructure. Infrastructure MAY serve several slices but SHALL NOT own a use-case type. A class whose behaviour
belongs to one feature SHALL move into that feature's slice package.

#### Scenario: Line renderer in a render package
- **WHEN** a class renders only the racing line
- **THEN** it belongs to the line slice's package in `voyager-platform`, not to a `render` package

#### Scenario: Message translation used by every slice
- **WHEN** a class translates message keys for several slices
- **THEN** it stays in the infrastructure `text` package

### Requirement: Composition root holds no use-case logic
**Priority:** MoSCoW Must

THE SYSTEM SHALL keep scoring, cup progression, standings and phase transitions in the use-case ring. The composition
root SHALL only wire, sequence and start these slices, and SHALL NOT compute a score, an ordering or a standing.

#### Scenario: Cup standings computed in the server module
- **WHEN** a class in `voyager-server` computes a standing or a cup score
- **THEN** the change is rejected until that logic moves into the cup or scoring slice of `voyager-race`

#### Scenario: Server sequences the tick
- **WHEN** the server calls the flight, boost and phase slices in a fixed order each tick
- **THEN** the order is allowed because it is wiring, and the logic of each step stays in its slice

### Requirement: Use-case slices are testable without a server
**Priority:** MoSCoW Must

THE SYSTEM SHALL give every use-case slice in `voyager-race` tests that build the slice's objects with `new`, drive it
with a fake clock and fake ports, and complete a whole race without starting a Minestom server.

#### Scenario: Full race in a unit test
- **WHEN** a test plays a complete race through the run, flow and scoring slices
- **THEN** the test completes with no server, no network and no file system access

### Requirement: Future slices keep the same rules
**Priority:** MoSCoW Should

WHERE a future slice is added to `voyager-setup` or `voyager-persistence`, THE SYSTEM SHALL place it under
`net.elytrarace.voyager.setup.<slice>..` or `net.elytrarace.voyager.persistence.<slice>..` and SHALL make it depend only
on the same ring rules and the same slice rules as its module's neighbours.

#### Scenario: Map setup slice
- **WHEN** the map setup slice is checked against this requirement
- **THEN** its contract sits in `net.elytrarace.voyager.api.mapsetup..`, its pure packages in `net.elytrarace.voyager.setup.mapsetup..`, and it depends on `voyager-platform` and `voyager-api` only. Its Minestom adapter code in `net.elytrarace.voyager.setup.adapter..` does not meet the "Framework adapters live in voyager-platform" requirement of `architecture/module-rings` and is migration item 26

### Requirement: Cup use cases are pure and live in the cup slice of race
**Priority:** MoSCoW Must

THE SYSTEM SHALL hold map scoring for a cup, cup standings, cup order, and the figures a racer's HUD shows during a map
in `voyager-race`, package `race.cup`. These types SHALL depend on no Minestom, Adventure, Xerus or platform type.

#### Scenario: Standings computed outside the cup slice
- **WHEN** a class outside `race.cup` computes a cup standing or a cup score
- **THEN** the change is rejected until that logic moves into `race.cup`

#### Scenario: Cup slice imports a platform type
- **WHEN** a class in `net.elytrarace.voyager.race.cup..` imports `net.minestom..` or `net.elytrarace.voyager.platform..`
- **THEN** the rule `raceDoesNotDependOnMinestomXerusOrItsWorldLoader` or `raceDoesNotDependOnPlatform` fails and names the class

### Requirement: The cup's Minestom adapter lives in platform
**Priority:** MoSCoW Must

THE SYSTEM SHALL hold the Minestom-facing adapter of a running cup, the announcer that sends its messages, and the mapping
from cup figures to HUD state in `voyager-platform`, in the packages `platform.cup` and `platform.hud`. The composition root
SHALL reach them only through their public types.

#### Scenario: Cup adapter in the server module
- **WHEN** a class in `net.elytrarace.voyager.server..` implements `RacePhaseListener` or sends cup messages to racers
- **THEN** the change is rejected until the adapter moves into `platform.cup`

#### Scenario: Cup use-case exception declared in platform
- **WHEN** a use-case exception of the cup (for example `UnresolvedCupException`) is declared in `platform`
- **THEN** the change is rejected in review, because `race.cup.exception` owns it, and `DesignRuleTest` still requires it to sit in an `exception` subpackage
