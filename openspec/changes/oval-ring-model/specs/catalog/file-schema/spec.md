# Spec Delta

## Purpose

The schemas follow the oval rings: `map.schema.json` and `cup.schema.json` describe version 3, the optional pair `radiusMinor`
and `majorAxis` is described on a ring, and the version 2 schemas are kept under their own names. The requirement that the
schema describes the current version now names version 3.

## MODIFIED Requirements

### Requirement: The schema accepts exactly the fields the adapters read
**Priority:** MoSCoW Must

THE SYSTEM SHALL make each schema's `required` list equal the fields its adapter refuses without, and SHALL allow the
optional fields `index`, `world`, `schemaVersion`, `notes`, `authors`, `radiusMinor`, `majorAxis` and `$schema` wherever the
format allows them. On a map or cup in version 2 the required identifier is `key`, and on a version 1 file it is `name`. On
a ring, `radiusMinor` and `majorAxis` SHALL be stated together, which the schema expresses as a dependency between them.

#### Scenario: Ring without index is valid
- **WHEN** a ring object omits `index` and has every other required field
- **THEN** the map schema accepts it

#### Scenario: Ring without center is invalid
- **WHEN** a ring object omits `center`
- **THEN** the map schema rejects it and the error names `center`

#### Scenario: Unknown ring type is invalid
- **WHEN** a ring's `type` is `"TELEPORT"`
- **THEN** the map schema rejects it, because the value is not a `RingType` name

#### Scenario: Cup mode is an enumeration
- **WHEN** a cup's `mode` is not `RACE` or `PRACTICE`
- **THEN** the cup schema rejects it

#### Scenario: Version 2 cup without key is invalid
- **WHEN** a version 2 cup omits `key`
- **THEN** the cup schema rejects it and the error names `key`

#### Scenario: Oval ring with both fields is valid
- **WHEN** a ring object states `radius`, `radiusMinor` and `majorAxis`
- **THEN** the map schema accepts it

#### Scenario: Oval ring without major axis is invalid
- **WHEN** a ring object states `radiusMinor` and omits `majorAxis`
- **THEN** the map schema rejects it and the error names `majorAxis`

### Requirement: The schema describes the current version only
**Priority:** MoSCoW Must

THE SYSTEM SHALL cap `schemaVersion` in `map.schema.json` and `cup.schema.json` at 3, the current version, and SHALL publish
the version 1 and version 2 schemas as `map-v1.schema.json`, `cup-v1.schema.json`, `map-v2.schema.json` and `cup-v2.schema.json`,
so that an editor reads a file against the schema of its own version. A version 4 file is reported as an error by the current
schemas.

#### Scenario: Version 2 in an editor
- **WHEN** a version 2 map file with a `key` is checked against `map.schema.json`
- **THEN** the schema accepts `schemaVersion` 2, because the current schema describes every version up to 3

#### Scenario: Version 3 oval file in an editor
- **WHEN** a version 3 map file with a ring that has `radiusMinor` and `majorAxis` is checked against `map.schema.json`
- **THEN** the schema accepts `schemaVersion` 3

#### Scenario: Version 2 file against its own schema
- **WHEN** a version 2 map file with a `key` is checked against `map-v2.schema.json`
- **THEN** the schema accepts it

#### Scenario: Version 1 file against its own schema
- **WHEN** a map file declaring no `schemaVersion` and a flat `name` is checked against `map-v1.schema.json`
- **THEN** the schema accepts it

#### Scenario: Version 4 in an editor
- **WHEN** a map file declaring `schemaVersion` 4 is checked against `map.schema.json`
- **THEN** the schema reports `schemaVersion` as out of range

## REMOVED Requirements

### Requirement: The schema still describes the current version only

This requirement caps `schemaVersion` at 1 in `map.schema.json`, a value that the change which introduced version 2
had already outdated. Its rule is the current version of "The schema describes the current version only" above,
which caps the schema at version 3 and names the version 4 refusal.
