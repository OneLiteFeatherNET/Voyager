## ADDED Requirements

### Requirement: The race-run holder and the map transition live in the cup slice of platform
**Priority:** MoSCoW Must

THE SYSTEM SHALL hold the per-racer race runs of the current map, and the transition that moves racers onto the next map
with a fresh run each, in `voyager-platform`, package `platform.cup`. The infrastructure package `platform.world` SHALL NOT
depend on classes of `platform.cup` and SHALL NOT depend on classes of `net.elytrarace.voyager.race..`. A run SHALL be
started only by the transition that puts the racer on the map's spawn, and dropped before any racer is moved.

#### Scenario: Race run held in the world package
- **WHEN** a class in `net.elytrarace.voyager.platform.world..` holds or advances a race run
- **THEN** the change is rejected until the holder moves into `platform.cup`, and the frozen rule that forbids race
  dependencies in `platform.world` names the class while its baseline entry exists

#### Scenario: Run started for a racer who has not arrived
- **WHEN** a class outside `platform.cup` gives a racer a run on a map
- **THEN** the change is rejected, because the start of a run is package-private to the transition that moves the racer

#### Scenario: Transition from a map that is not there
- **WHEN** the transition is given a map whose world is not available
- **THEN** every racer keeps the run they held, because the world is resolved before any run is dropped

#### Scenario: World depends on the cup
- **WHEN** a class in `net.elytrarace.voyager.platform.world..` depends on a class in `net.elytrarace.voyager.platform.cup..`
- **THEN** the change is rejected in review, because the cup is a consumer of the world and not the reverse
