# Spec Delta

## Purpose

Makes `map.schema.json` describe the optional boost and ring-effect fields, so an editor checks them as the loader reads
them. The schema still describes schema version 1 only; this change adds fields to it and does not raise the cap.

## ADDED Requirements

### Requirement: The map schema describes the optional boost and ring-effect fields
**Priority:** MoSCoW Must. THE SYSTEM SHALL list `boostConfig.rocketsPerRacer`, `boostConfig.maxSpeedBlocksPerTick` and the
top-level `ringEffects` object, with their bounds, defaults and descriptions, and SHALL keep `required` equal to the fields
the adapter refuses without.

#### Scenario: Rockets out of range in an editor
- **WHEN** a map file states `"rocketsPerRacer": 0`
- **THEN** `map.schema.json` reports `rocketsPerRacer` as out of range

#### Scenario: A ringEffects factor below zero in an editor
- **WHEN** a map file states `"slowMultiplier": -0.5`
- **THEN** `map.schema.json` reports `slowMultiplier` as out of range

#### Scenario: Required fields are unchanged
- **WHEN** a map's `boostConfig` omits `burnDurationTicks`
- **THEN** `map.schema.json` reports the missing field

### Requirement: The schema still describes the current version only
**Priority:** MoSCoW Must. THE SYSTEM SHALL keep `schemaVersion` capped at 1 in `map.schema.json` after this change.

#### Scenario: Version 2 remains an error in the schema
- **WHEN** a map file declaring `schemaVersion` 2 is checked against `map.schema.json`
- **THEN** the schema reports `schemaVersion` as out of range, as before this change
