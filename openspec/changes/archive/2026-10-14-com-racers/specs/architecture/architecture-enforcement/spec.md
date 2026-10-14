# Delta for architecture/architecture-enforcement

Adds the rules that name the computer racer boundary (design D9). Every rule uses `allowEmptyShould(false)` and is not
frozen in `archunit_store`, because the new code starts clean.

## ADDED Requirements

### Requirement: The computer racer brain is pure
**MoSCoW:** Must. THE SYSTEM SHALL fail the build if a class in `net.elytrarace.voyager.race.com..` depends on
`net.minestom..` or `net.elytrarace.voyager.platform..`, or calls `System.currentTimeMillis`, `System.nanoTime`,
`Instant.now`, `Math.random`, or the no-argument `Random` constructor.

#### Scenario: Minestom import in the brain
- **WHEN** a class in `race.com` imports a Minestom type
- **THEN** `comBrainIsPure` fails and names the class

#### Scenario: Wall-clock read in the brain
- **WHEN** a class in `race.com` calls `System.nanoTime`
- **THEN** `comBrainIsPure` fails and names the class

### Requirement: The cup does not depend on the computer racer platform code
**MoSCoW:** Must. THE SYSTEM SHALL fail the build if a class in `net.elytrarace.voyager.platform.cup..` depends on
`net.elytrarace.voyager.platform.com..`.

#### Scenario: Cup imports a computer racer adapter
- **WHEN** `CupSession` imports `ComField`
- **THEN** `cupDoesNotDependOnComPlatform` fails

### Requirement: Computer racers do not simulate flight or touch the flight tracker
**MoSCoW:** Must. THE SYSTEM SHALL fail the build if a class in `race.com..` or `platform.com..` depends on
`net.elytrarace.voyager.physics..` or `net.elytrarace.voyager.platform.flight..`.

#### Scenario: Bot uses the flight tracker
- **WHEN** a class in `platform.com` imports `FlightTracker`
- **THEN** `comAdapterDoesNotSimulateFlight` fails

### Requirement: Computer racer settings are read only in the composition root
**MoSCoW:** Must. THE SYSTEM SHALL fail the build if a class outside `net.elytrarace.voyager.server.config..` and
`net.elytrarace.voyager.server.inject..` references the computer racer property names.

#### Scenario: Race code reads the count
- **WHEN** a class in `race.com` references `VOYAGER_COM_COUNT`
- **THEN** `onlyServerConfigReadsComSettings` fails

### Requirement: The computer racer slice is covered by the existing slice rules
**MoSCoW:** Must. THE SYSTEM SHALL keep `platform.com` and `race.com` under the slice-cycle rules r4 and r7 of
`SliceBoundaryRulesTest`, which match their package patterns without a change.

#### Scenario: New slice is acyclic
- **WHEN** `com` introduces a cycle with another slice in `race` or `platform`
- **THEN** r4 or r7 fails
