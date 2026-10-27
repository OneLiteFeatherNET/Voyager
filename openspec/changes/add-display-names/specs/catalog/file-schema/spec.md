# Spec Delta

## Purpose

The cup and map schemas describe the optional display name, so an editor that validates against the schema accepts it, and
the adapter reads it.

## MODIFIED Requirements

### Requirement: The schema accepts exactly the fields the adapters read
**Priority:** MoSCoW Must

THE SYSTEM SHALL make each schema's `required` list equal the fields its adapter refuses without, and SHALL allow the
optional fields `index`, `world`, `schemaVersion`, `notes`, `displayName` and `$schema` wherever the format allows them.
The `displayName` field is allowed on a cup and on a map.

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

#### Scenario: Display name is allowed on a cup and on a map
- **WHEN** a cup or a map has a string `displayName`
- **THEN** its schema accepts the field
