# Spec Delta

## Purpose

Defines how the architecture requirements of this capability set are checked. It names the rules in `voyager-fitness` that
already enforce part of the rings, slices and DI boundaries, and it specifies the rules that the follow-up change
`add-architecture-slice-rules` must add. Its aim is that every requirement either fails the build when broken or is named as a
gap with an owner.

## ADDED Requirements

### Requirement: Every architecture requirement has an enforcing rule or a named gap
**Priority:** MoSCoW Must

THE SYSTEM SHALL map each requirement of `architecture/module-rings`, `architecture/vertical-slices` and
`architecture/dependency-injection-boundaries` to either an `@ArchTest` rule in `voyager-fitness` or a named follow-up change.
A requirement with neither SHALL be reported in review as unenforced.

#### Scenario: Requirement without a rule
- **WHEN** a reviewer finds a requirement with no rule and no follow-up name
- **THEN** the review records it as a gap and the change is not marked done

### Requirement: Existing rules guard entity and use-case purity
**Priority:** MoSCoW Must

THE SYSTEM SHALL keep the existing `ApiPurityTest` rules that forbid Minestom, Paper, Xerus, persistence, DI and file I/O in
`voyager-api` and `voyager-physics`, and the rules that forbid Minestom, Xerus, Paper, persistence and platform references in
`voyager-race`, including `raceDoesNotDependOnPlatform`, `physicsDoesNotDependOnRaceOrPlatform` and `apiDoesNotDependOnPlatform`.

#### Scenario: Minestom import in api
- **WHEN** a class in `net.elytrarace.voyager.api..` imports a Minestom type
- **THEN** `apiDoesNotDependOnMinestomOrItsWorldLoader` fails and names the class

#### Scenario: Platform import in race
- **WHEN** a class in `net.elytrarace.voyager.race..` imports `net.elytrarace.voyager.platform..`
- **THEN** `raceDoesNotDependOnPlatform` fails and names the class

### Requirement: Platform types are confined to their converters
**Priority:** MoSCoW Must

THE SYSTEM SHALL keep the existing confinement rules that name the only classes allowed to touch a Minestom or Gson type or to
build a user-facing component: `onlyBlockShapesReachesForMinestomsShapeImplementation`, `onlyVelocityExitSendsAVelocityToMinestom`,
`onlyVectorsBuildsAMinestomVectorFromDomainCoordinates`, `onlyPlatformDependsOnGson` and `onlyTheTextPackageBuildsAUserFacingString`.

#### Scenario: Velocity sent outside the exit
- **WHEN** a class other than `VelocityExit` calls `setVelocity` on a Minestom type
- **THEN** `onlyVelocityExitSendsAVelocityToMinestom` fails

### Requirement: Ring order is enforced by a layered rule
**Priority:** MoSCoW Must

THE SYSTEM SHALL check the four rings with an ArchUnit `layeredArchitecture()` rule in which each ring is accessed only by the rings outside it.

#### Scenario: Adapter accessed by a use case
- **WHEN** a class in `..race..` depends on a class in `..platform..`
- **THEN** the layered rule fails and names both classes

#### Scenario: Entity accessed by a composition root
- **WHEN** a class in `..server..` is accessed by a class in `..api..`
- **THEN** the layered rule fails, because the composition root may not be accessed by any ring

### Requirement: Ring layers are defined by package
**Priority:** MoSCoW Must

THE SYSTEM SHALL define the layers Entities (`..api..`, `..physics..`), UseCases (`..race..`), Adapters (`..platform..`,
`..persistence..`) and Composition (`..server..`, `..setup..`) in the layered rule, so that every rebuild production class
belongs to exactly one layer.

#### Scenario: Class outside every layer
- **WHEN** a production class in a rebuild module matches none of the layer packages
- **THEN** the layered rule fails and names the class

### Requirement: Each ring layer is non-empty
**Priority:** MoSCoW Must

THE SYSTEM SHALL fail the layered rule when a layer matches no class, so that the rule cannot pass vacuously.

#### Scenario: Persistence not yet present
- **WHEN** `..persistence..` does not exist yet
- **THEN** the Adapters layer is still checked against `..platform..`, and the rule does not pass on an empty layer
### Requirement: Slice cycles are forbidden by a rule
**Priority:** MoSCoW Must

THE SYSTEM SHALL add `slices().matching(...).should().beFreeOfCycles()` rules for the slice packages of `voyager-race`
(`net.elytrarace.voyager.race.(*)..`), of `voyager-platform` (its feature slices, excluding the infrastructure packages named in
`architecture/vertical-slices`), and of `voyager-api` (`net.elytrarace.voyager.api.(*)..`), each with `allowEmptyShould(false)`.

#### Scenario: Scoring depends on run and run on scoring
- **WHEN** a class in `race.scoring` depends on `race.run` and a class in `race.run` depends on `race.scoring`
- **THEN** the cycle rule fails and reports both slices

### Requirement: Slice internals stay private
**Priority:** MoSCoW Must

THE SYSTEM SHALL add, for every slice that has an `internal` subpackage, a rule that no class outside that slice depends on
`<slice>.internal..`. The rule SHALL be generated from the slice list so that a new slice receives it without a manual edit.

#### Scenario: Internal class imported by another slice
- **WHEN** a class in `race.scoring` imports `net.elytrarace.voyager.race.run.internal..`
- **THEN** the internal-package rule fails and names the importing class

### Requirement: Avaje types are confined to the composition roots
**Priority:** MoSCoW Must

THE SYSTEM SHALL forbid `io.avaje.inject..` outside `..server..` and `..setup..`, and SHALL forbid `io.avaje.inject.BeanScope` outside `..server.inject..` and `VoyagerServer`.

#### Scenario: BeanScope used in a slice
- **WHEN** a class in `..race.cup..` references `io.avaje.inject.BeanScope`
- **THEN** the DI rule fails the build

#### Scenario: Avaje type in a domain module
- **WHEN** a class in `voyager-race` imports `io.avaje.inject..`
- **THEN** the DI rule fails the build

### Requirement: Platform classes carry no DI annotation
**Priority:** MoSCoW Must

THE SYSTEM SHALL forbid any `jakarta.inject..` or `io.avaje.inject..` annotation on `..platform..` classes, their constructors,
methods and fields.

#### Scenario: Avaje annotation in platform
- **WHEN** a class in `..platform..` carries `@Factory` or `@Bean`
- **THEN** the DI rule fails the build

#### Scenario: Inject on a platform constructor
- **WHEN** a constructor of a platform class is annotated `@Inject`
- **THEN** the DI rule fails the build, because platform classes are wired by the composition root

### Requirement: jakarta.inject is confined to the composition roots
**Priority:** MoSCoW Must

THE SYSTEM SHALL forbid `jakarta.inject..` in `voyager-api`, `voyager-physics`, `voyager-race` and `voyager-platform`, and outside
`..server..` and `..setup..` in every rebuild module.

#### Scenario: Inject in a use case
- **WHEN** a class in `..race..` carries `@Inject`
- **THEN** the DI rule fails the build

#### Scenario: Inject in a platform class
- **WHEN** a class in `..platform..` carries `@Inject`
- **THEN** the DI rule fails the build
### Requirement: Mutable static state and clock reads are forbidden in the inner rings
**Priority:** MoSCoW Should

WHERE the inner rings are checked, THE SYSTEM SHALL add rules that forbid a non-final static field in any rebuild module, and
forbid calls to `System.currentTimeMillis`, `System.nanoTime` and `Instant.now` in `..api..`, `..physics..` and `..race..`. The
rules SHALL use the ArchUnit 1.4.2 API, and the exact method signatures SHALL be verified against that version during apply.

#### Scenario: Static counter in a use case
- **WHEN** a class in `..race..` declares a non-final static field
- **THEN** the static-state rule fails

#### Scenario: Clock read in physics
- **WHEN** a class in `..physics..` calls `Instant.now()`
- **THEN** the clock rule fails, and the caller must take a `java.time.Clock` instead

### Requirement: Server holds no scoring or cup logic once the move is complete
**Priority:** MoSCoW Should

WHEN the follow-up change `move-cup-flow-out-of-server` is merged, THE SYSTEM SHALL add a rule that no class in `..server..`
depends on `net.elytrarace.voyager.race.scoring..`, so that a later scoring change cannot grow back into the composition root.

#### Scenario: Scoring import in server after the move
- **WHEN** a class in `..server.game..` imports `race.scoring` after the move is complete
- **THEN** the rule fails and names the class

### Requirement: No architecture rule passes vacuously
**Priority:** MoSCoW Must

THE SYSTEM SHALL declare every new architecture rule with `allowEmptyShould(false)`, and SHALL keep `FitnessCoverageTest` in place
so that every module on the classpath is still imported and named by at least one rule.

#### Scenario: Rule matches nothing
- **WHEN** a new rule's package pattern matches no class
- **THEN** the rule fails instead of passing

#### Scenario: Module added without a rule
- **WHEN** a rebuild module is added and no rule names its package prefix
- **THEN** `FitnessCoverageTest` fails

### Requirement: Rules enter the suite only after the violations they flag are fixed
**Priority:** MoSCoW Must

THE SYSTEM SHALL add an enforcing rule to the suite only in the same change as, or after, the fixes for the violations listed in
`design.md` that the rule would flag. A rule SHALL NOT be merged red, and SHALL NOT be excluded from the build to make it pass.

#### Scenario: Cycle rule before the fix
- **WHEN** a rule would fail on code that the migration list still records as open
- **THEN** the rule is held back until the open item is closed, and the change records the dependency

