# Spec Delta

## Purpose

Adds the optional boost and ring-effect fields to the map file format. The fields are additive: a file without them loads
exactly as it did, and a file with them is read the same way under schema version 1 and schema version 2.

## ADDED Requirements

### Requirement: The boost configuration accepts two optional fields
**Priority:** MoSCoW Must. THE SYSTEM SHALL accept `boostConfig.rocketsPerRacer`, an integer from 1 to 64, and
`boostConfig.maxSpeedBlocksPerTick`, a finite number greater than zero, in addition to the two required fields
`burnDurationTicks` and `cooldownTicks`, which keep their meaning and their required status.

#### Scenario: A boost configuration with only the required fields
- **WHEN** a map's `boostConfig` states `burnDurationTicks` and `cooldownTicks` and no other field
- **THEN** the file loads, with 64 rockets and no speed cap

#### Scenario: A boost configuration without its required fields
- **WHEN** a map's `boostConfig` omits `cooldownTicks`
- **THEN** the file is refused and the message names `cooldownTicks`, as before this change

### Requirement: The map file accepts an optional ringEffects object
**Priority:** MoSCoW Must. THE SYSTEM SHALL accept a `ringEffects` object at the top level of a map file, holding the optional
numbers `boostMultiplier` and `slowMultiplier`, and SHALL read a map without it with the default factors.

#### Scenario: A map file with ringEffects in version 2
- **WHEN** a map file declares `"schemaVersion": 2` and states `ringEffects` with `"boostMultiplier": 2`
- **THEN** the file loads and its BOOST factor is 2

#### Scenario: A map file with ringEffects and no version
- **WHEN** a map file states `ringEffects` and no `schemaVersion`
- **THEN** the file loads under the version 1 rules with that BOOST factor

#### Scenario: Unknown fields inside boostConfig are still ignored
- **WHEN** a map's `boostConfig` states a field the loader does not know
- **THEN** the file loads and the unknown field is ignored, as before this change

### Requirement: A map file without the new fields loads as before
**Priority:** MoSCoW Must. THE SYSTEM SHALL load every map file that the server loaded before this change to the same
map definition, except that its BOOST and SLOW rings now carry their effects.

#### Scenario: The shipped map loads with its values
- **WHEN** the shipped map `elytraraceblueandred` is loaded
- **THEN** it has 64 rockets, no speed cap, a BOOST factor of 1.5 and a SLOW factor of 0.5
