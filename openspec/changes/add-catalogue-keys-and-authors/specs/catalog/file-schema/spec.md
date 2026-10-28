# Spec Delta

## Purpose

The schemas follow the format change: the version 2 schema describes the key and the authors, and the version 1 schema is
kept for the files that still use a flat name.

## MODIFIED Requirements

### Requirement: The schema accepts exactly the fields the adapters read
**Priority:** MoSCoW Must

THE SYSTEM SHALL make each schema's `required` list equal the fields its adapter refuses without, and SHALL allow the
optional fields `index`, `world`, `schemaVersion`, `notes`, `authors` and `$schema` wherever the format allows them. On a
map or cup in version 2 the required identifier is `key`, and on a version 1 file it is `name`.

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

### Requirement: The schema describes the current version only
**Priority:** MoSCoW Must

THE SYSTEM SHALL cap `schemaVersion` in `map.schema.json` and `cup.schema.json` at 2, the current version, and SHALL publish the
version 1 schemas as `map-v1.schema.json` and `cup-v1.schema.json`, so that an editor reads a version 1 file against its own
schema. A version 3 file is reported as an error by the current schemas.

#### Scenario: Version 2 in an editor
- **WHEN** a version 2 map file with a `key` is checked against `map.schema.json`
- **THEN** the schema accepts `schemaVersion` 2

#### Scenario: Version 3 in an editor
- **WHEN** a map file declaring `schemaVersion` 3 is checked against `map.schema.json`
- **THEN** the schema reports `schemaVersion` as out of range

#### Scenario: Version 1 file against its own schema
- **WHEN** a map file declaring no `schemaVersion` and a flat `name` is checked against `map-v1.schema.json`
- **THEN** the schema accepts it
