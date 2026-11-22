# catalog/file-schema Specification

## Purpose
Defines the JSON Schema documents for map and cup files. The schemas exist so an editor can complete and check a
hand-written file while it is edited. They describe the format the adapters already enforce; they do not replace the
adapters as the runtime check.

## Requirements

### Requirement: A schema document exists for each file kind
**Priority:** MoSCoW Must

THE SYSTEM SHALL ship one JSON Schema document for map files and one for cup files, both declaring draft 2020-12 and a
stable `$id`, under `voyager/platform/src/main/resources/schema/` as `map.schema.json` and `cup.schema.json`. The `$id` of
each document SHALL be the raw URL of that file in the repository's `main` branch, so it names the file's location.

#### Scenario: Schema declares its draft
- **WHEN** either schema document is parsed
- **THEN** its `$schema` is the 2020-12 meta-schema URI and it declares a non-empty `$id`

### Requirement: The schema accepts exactly the fields the adapters read
**Priority:** MoSCoW Must

THE SYSTEM SHALL make each schema's `required` list equal the fields its adapter refuses without, and SHALL allow the
optional fields `index`, `world`, `schemaVersion`, `notes`, `displayName`, `authors`, `radiusMinor`, `majorAxis` and `$schema`
wherever the format allows them. On a map or cup in version 2 the required identifier is `key`, and on a version 1 file it is
`name`. On a ring, `radiusMinor` and `majorAxis` SHALL be stated together, which the schema expresses as a dependency between
them. The `displayName` field is allowed on a cup and on a map.

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

#### Scenario: Display name is allowed on a cup and on a map
- **WHEN** a cup or a map has a string `displayName`
- **THEN** its schema accepts the field

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

### Requirement: Shipped files reference their schema
**Priority:** MoSCoW Must

THE SYSTEM SHALL have every shipped map and cup file declare `$schema` as a relative path to the schema document of its
kind, and that path SHALL resolve to an existing file in the repository.

#### Scenario: Shipped map resolves its schema
- **WHEN** `voyager/server/src/main/resources/maps/elytraraceblueandred.json` is read
- **THEN** its `$schema` resolves to `voyager/platform/src/main/resources/schema/map.schema.json`

#### Scenario: Shipped cup resolves its schema
- **WHEN** `voyager/server/src/main/resources/cups/test_cup.json` is read
- **THEN** its `$schema` resolves to `voyager/platform/src/main/resources/schema/cup.schema.json`

### Requirement: Committed files validate against their schema
**Priority:** MoSCoW Should

THE SYSTEM SHALL check, in a test, that every committed map and cup file validates against its schema, and that a set of
deliberately invalid fixtures is rejected by it. The validator is a test-scope dependency of `voyager-platform`.

#### Scenario: Committed map validates
- **WHEN** the committed map file is validated against `map.schema.json` in the test
- **THEN** the validation reports no errors

#### Scenario: Invalid fixture is rejected
- **WHEN** a fixture with a ring missing `radius` is validated
- **THEN** the validation reports an error that names `radius`

### Requirement: Load-time schema validation is not performed
**Priority:** MoSCoW Won't (this change)

THE SYSTEM SHALL NOT validate map or cup files against the JSON Schema when the server loads them. The adapters remain the
only runtime check, and a file that fails the schema but passes the adapters loads.

#### Scenario: Server loads a file the schema would flag
- **WHEN** a map file has an unknown extra top-level key that the schema rejects and the adapters ignore
- **THEN** the server loads the file without consulting the schema

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
