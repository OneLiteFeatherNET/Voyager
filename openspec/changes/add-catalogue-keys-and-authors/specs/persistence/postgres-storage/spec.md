# Spec Delta

## Purpose

Statistics are keyed by the catalogue key, so a map or cup keeps its history after its identifier changes from a flat id to a
key.

## ADDED Requirements

### Requirement: Statistics are keyed by the catalogue key, and existing rows are rewritten once
**MoSCoW:** Must. THE SYSTEM SHALL store a map's `map_key` and a cup's `cup_key` as the catalogue key, in the form
`elytrarace:<value>`. A Flyway migration SHALL rewrite every existing `map_key` and `cup_key` value that is a flat id in the
migration mapping of the design to its key, in one transaction, and SHALL leave a value that is not in the mapping unchanged.
The `layout_version` SHALL NOT change.

#### Scenario: A personal best written before the change
- **WHEN** the migration runs over a row whose `map_key` is `elytraraceblueandred`
- **THEN** the row's `map_key` is `elytrarace:blue_and_red` and its `layout_version` is unchanged

#### Scenario: A row for a map no longer in the catalogue
- **WHEN** the migration runs over a row whose `map_key` is not in the mapping
- **THEN** the row is left unchanged and the migration does not fail

#### Scenario: A new completion is stored under the key
- **WHEN** a map finishes after the migration
- **THEN** the completion is written with `map_key` `elytrarace:<value>`
