# Spec Delta

## Purpose

Defines the JSON Schema documents for map and cup files. The schemas exist so an editor can complete and check a
hand-written file while it is edited. They describe the format the adapters already enforce; they do not replace the
adapters as the runtime check.

## ADDED Requirements

### Requirement: A schema document exists for each file kind
**Priority:** MoSCoW Must

THE SYSTEM SHALL ship one JSON Schema document for map files and one for cup files, both declaring draft 2020-12 and a
stable `$id`, under `voyager-platform/src/main/resources/schema/` as `map.schema.json` and `cup.schema.json`.

#### Scenario: Schema declares its draft
- **WHEN** either schema document is parsed
- **THEN** its `$schema` is the 2020-12 meta-schema URI and it declares a non-empty `$id`

### Requirement: The schema accepts exactly the fields the adapters read
**Priority:** MoSCoW Must

THE SYSTEM SHALL make each schema's `required` list equal the fields its adapter refuses without, and SHALL allow the
optional fields `index`, `world`, `schemaVersion`, `notes` and `$schema` wherever the format allows them.

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

### Requirement: The schema describes the current version only
**Priority:** MoSCoW Must

THE SYSTEM SHALL cap `schemaVersion` in each schema at 1, so a schema read by an editor reports a version 2 file as an
error, and SHALL publish a new schema document under a new file name when a later version is introduced.

#### Scenario: Version 2 in an editor
- **WHEN** a map file declaring `schemaVersion` 2 is checked against `map.schema.json`
- **THEN** the schema reports `schemaVersion` as out of range

### Requirement: Shipped files reference their schema
**Priority:** MoSCoW Must

THE SYSTEM SHALL have every shipped map and cup file declare `$schema` as a relative path to the schema document of its
kind, and that path SHALL resolve to an existing file in the repository.

#### Scenario: Shipped map resolves its schema
- **WHEN** `voyager-server/src/main/resources/maps/elytraraceblueandred.json` is read
- **THEN** its `$schema` resolves to `voyager-platform/src/main/resources/schema/map.schema.json`

#### Scenario: Shipped cup resolves its schema
- **WHEN** `voyager-server/src/main/resources/cups/test_cup.json` is read
- **THEN** its `$schema` resolves to `voyager-platform/src/main/resources/schema/cup.schema.json`

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
