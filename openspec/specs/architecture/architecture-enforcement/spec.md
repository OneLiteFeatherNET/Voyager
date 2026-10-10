# architecture/architecture-enforcement Specification

## Purpose
Defines how the architecture requirements of this capability set are checked. It names the rules in `voyager-fitness` that
already enforce part of the rings, slices and DI boundaries, and it specifies the rules that the follow-up change
`add-architecture-slice-rules` must add. Its aim is that every requirement either fails the build when broken or is named as a
gap with an owner.

## Requirements

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

THE SYSTEM SHALL add `slices().matching(...).should().beFreeOfCycles()` rules for the slice packages of `voyager-race` (`net.elytrarace.voyager.race.(*)..`), of `voyager-platform` (`net.elytrarace.voyager.platform.(*)..`, infrastructure packages included, because a cycle through infrastructure is still a cycle) and of `voyager-api` (`net.elytrarace.voyager.api.(*)..`), each with `allowEmptyShould(false)`. The platform rule SHALL be frozen with its known violations.

#### Scenario: Scoring depends on run and run on scoring
- **WHEN** a class in `race.scoring` depends on `race.run` and a class in `race.run` depends on `race.scoring`
- **THEN** the cycle rule fails and reports both slices

#### Scenario: Catalog and world depend on each other
- **WHEN** a class in `platform.catalog` depends on `platform.world` and a class in `platform.world` depends on `platform.catalog`
- **THEN** the cycle rule reports both packages, and the violation is in the platform rule's baseline under migration item 27 until that item is fixed

### Requirement: Slice internals stay private
**Priority:** MoSCoW Must

THE SYSTEM SHALL add, for every slice that has an `internal` subpackage, a rule that no class outside that slice depends on
`<slice>.internal..`. The rule SHALL be generated from the slice list so that a new slice receives it without a manual edit.

#### Scenario: Internal class imported by another slice
- **WHEN** a class in `race.scoring` imports `net.elytrarace.voyager.race.run.internal..`
- **THEN** the internal-package rule fails and names the importing class

### Requirement: Avaje types are confined to the composition roots
**Priority:** MoSCoW Must

THE SYSTEM SHALL forbid `io.avaje.inject..` outside `..server..` and `..setup..`, and SHALL forbid `io.avaje.inject.BeanScope` outside `..server.inject..`, `VoyagerServer`, `..setup.inject..` and `SetupServer`.

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

THE SYSTEM SHALL keep a rule that no class in `..server..` depends on `net.elytrarace.voyager.race.scoring..`. The rule SHALL be frozen with its known violations, migration items 1 to 3, until the change `extract-cup-slice` (which supersedes the name `move-cup-flow-out-of-server` in the archived migration list) is merged; after that change the baseline for this rule SHALL be empty and the rule SHALL be plain.

#### Scenario: Scoring import in server after the move
- **WHEN** a class in `..server.game..` imports `race.scoring` after the move is complete
- **THEN** the rule fails and names the class

#### Scenario: Scoring import in server before the move
- **WHEN** a class in `..server..` that is not in the baseline imports `race.scoring` before the move is complete
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

### Requirement: Minestom stays out of the setup module
**Priority:** MoSCoW Must

THE SYSTEM SHALL forbid `net.minestom..` in every class of `net.elytrarace.voyager.setup..` outside the composition root, so that Minestom adapter code lives in `voyager-platform` as `architecture/module-rings` requires. The rule SHALL be frozen with the baseline of migration item 26, and SHALL be plain once that item is fixed.

#### Scenario: Minestom listener in the setup module
- **WHEN** a class in `net.elytrarace.voyager.setup.adapter..` that is not in the baseline imports `net.minestom..`
- **THEN** the rule fails and names the class; the seven classes of `setup.adapter` that the baseline records do not fail it

#### Scenario: Minestom bootstrap in the composition root
- **WHEN** `net.elytrarace.voyager.setup.SetupServer` or a class of `net.elytrarace.voyager.setup.inject..` (including its avaje-generated classes, which wire the Minestom bootstrap and are excluded as wiring) imports `net.minestom..`
- **THEN** the rule does not fail, because the composition root is outside its scope

### Requirement: Rules enter the suite only after the violations they flag are fixed
**Priority:** MoSCoW Must

THE SYSTEM SHALL add an enforcing rule to the suite only when it passes on `main`, or when every violation it flags is recorded in its frozen baseline with a migration item, or when the fixes for the violations it would flag are in the same change. A rule SHALL NOT be merged red, and SHALL NOT be excluded from the build to make it pass.

#### Scenario: Cycle rule before the fix
- **WHEN** a rule would fail on code that the migration list still records as open
- **THEN** the rule is frozen with those violations in its baseline, and the change records the migration items that the baseline covers

#### Scenario: Rule excluded to pass
- **WHEN** a rule is excluded from the build, or its package pattern is narrowed, only to avoid a known violation
- **THEN** the change is rejected in review

### Requirement: Known slice-boundary violations are frozen in a committed baseline
**Priority:** MoSCoW Must

WHERE a slice-boundary rule already has violations on `main` when it is added, THE SYSTEM SHALL freeze that rule with `FreezingArchRule`, and SHALL keep its known violations in a violation store that is committed under the `voyager-fitness` test resources.

#### Scenario: Frozen rule with known violations
- **WHEN** the fitness suite runs and a frozen rule finds only violations that are already in its stored baseline
- **THEN** the rule passes and the test run reports no failure for those violations

#### Scenario: Rule without known violations
- **WHEN** a slice-boundary rule has no violation on `main`
- **THEN** the rule is not frozen and has no store entry, so any violation fails it at once

### Requirement: New violations of a frozen rule fail the build
**Priority:** MoSCoW Must

WHEN a frozen rule finds a violation that its stored baseline does not contain, THE SYSTEM SHALL fail the fitness test run, locally and on CI, and the failure SHALL name the rule and the class.

#### Scenario: New import of scoring into the server
- **WHEN** a class in `net.elytrarace.voyager.server..` that is not in the baseline imports `net.elytrarace.voyager.race.scoring..`
- **THEN** the server-scoring rule fails and names that class

#### Scenario: Frozen rule matches no class
- **WHEN** the package pattern of a frozen rule matches no class
- **THEN** the rule fails instead of passing on the stored baseline, as `No architecture rule passes vacuously` requires

### Requirement: CI refuses to create or update a baseline
**Priority:** MoSCoW Must

WHILE the environment variable `CI` is `true`, THE SYSTEM SHALL disable the creation of a new violation store and the update of an existing one for the fitness test run, so that a baseline cannot be written during a CI run.

#### Scenario: New frozen rule without a committed entry on CI
- **WHEN** a frozen rule has no entry in the committed `stored.rules` and the test run executes on CI
- **THEN** the test fails and reports that updating frozen violations is disabled, because the store exists and only its
  creation is refused; the entry must be committed with its migration item

#### Scenario: Committed store missing on CI
- **WHEN** the committed store directory has no `stored.rules` and the test run executes on CI
- **THEN** the test fails and reports that creating a violation store is disabled

#### Scenario: Fixed violation still in the store on CI
- **WHEN** a violation recorded in the committed store no longer occurs and the test run executes on CI
- **THEN** the test fails and reports that updating frozen violations is disabled, so the shrunk store must be committed

### Requirement: A fixed violation leaves the baseline in the same change
**Priority:** MoSCoW Must

WHEN a developer fixes a violation recorded in the baseline, THE SYSTEM SHALL remove that violation from the store during a local fitness test run, and the fix and the shrunk store SHALL be committed together. A store entry SHALL NOT be added by hand, and SHALL NOT be added by a refreeze run, without an owner-approved migration item recorded in the design of this change.

#### Scenario: Local run after a fix
- **WHEN** a developer fixes a recorded violation and runs the fitness tests locally
- **THEN** the store file no longer contains that violation and the test passes

#### Scenario: Store entry added without an item
- **WHEN** a store change adds an entry that no migration item of the design of this change names
- **THEN** the change is rejected in review until the owner approves an item for it

### Requirement: Every baseline entry maps to a migration-list item
**Priority:** MoSCoW Should

THE SYSTEM SHALL keep, in the design of the change that introduces a baseline, a table that maps each stored violation to a migration-list item number. A stored violation that no item names SHALL be added to that table as an addendum item before the baseline is committed.

#### Scenario: Violation without an item
- **WHEN** the first run of a frozen rule records a violation that no migration item names
- **THEN** the design of the change gains an addendum item for it, and the owner is asked to confirm the item number

### Requirement: Infrastructure packages do not depend on race slices
**Priority:** MoSCoW Must

THE SYSTEM SHALL forbid classes in the infrastructure packages `text`, `convert`, `world`, `tick` and `render` of `voyager-platform` from depending on classes of `net.elytrarace.voyager.race..`. The rule SHALL be frozen with its known violations until those are fixed.

#### Scenario: Flow class in the tick package
- **WHEN** a class in `net.elytrarace.voyager.platform.tick..` depends on a class in `net.elytrarace.voyager.race.flow..`
- **THEN** the rule fails, unless that dependency is in the baseline, and names the class

#### Scenario: Rule matches a type from the API module
- **WHEN** a class in `net.elytrarace.voyager.platform.world..` depends only on types in `net.elytrarace.voyager.api.race..`
- **THEN** the rule does not fail, because the API module is not a race slice

### Requirement: The server's game package holds no Minestom adapter
**Priority:** MoSCoW Must

THE SYSTEM SHALL forbid classes of `net.elytrarace.voyager.server.game..` from depending on `net.minestom..`. The rule SHALL be frozen with its known violations until the migration items that name them are fixed. The server's bootstrap class, outside that package, is not covered.

#### Scenario: Minestom type in the game package
- **WHEN** a class in `net.elytrarace.voyager.server.game..` that is not in the baseline imports `net.minestom..`
- **THEN** the rule fails and names the class

### Requirement: Platform classes that use mapsetup contracts live in the mapsetup slice
**Priority:** MoSCoW Should

THE SYSTEM SHALL require every class of `net.elytrarace.voyager.platform..` that depends on `net.elytrarace.voyager.api.mapsetup..` to reside in `net.elytrarace.voyager.platform.mapsetup..`. The rule SHALL be frozen with its known violations until they are moved.

#### Scenario: Draft store in the catalog package
- **WHEN** a class in `net.elytrarace.voyager.platform.catalog..` that is not in the baseline depends on `net.elytrarace.voyager.api.mapsetup..`
- **THEN** the rule fails and names the class
