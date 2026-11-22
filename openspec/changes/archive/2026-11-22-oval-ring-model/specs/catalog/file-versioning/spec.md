# Spec Delta

## Purpose

The highest schema version this server reads becomes 3, the version that states an oval ring. The refusal of a newer version
is unchanged in its rule, and its examples move to version 4.

## MODIFIED Requirements

### Requirement: A newer schema version is refused with the supported maximum
**Priority:** MoSCoW Must

WHEN a map or cup file states a `schemaVersion` above the highest version this server reads, THE SYSTEM SHALL refuse
that file, and the message SHALL name the file, the declared version and the highest supported version. The highest
version this server reads is 3.

#### Scenario: Map written for a future format
- **WHEN** a map file states `"schemaVersion": 4` and this server reads up to version 3
- **THEN** the file is refused and the message names the file, `4` and `3`

#### Scenario: Refusal loads nothing from the file
- **WHEN** a map file with `schemaVersion` 4 is refused
- **THEN** no ring, guide point, world or key from that file reaches the catalogue

### Requirement: The rule applies to maps and cups alike
**Priority:** MoSCoW Must

THE SYSTEM SHALL apply the same schema version rule to map files and cup files, with the same messages.

#### Scenario: Cup written for a future format
- **WHEN** a cup file states `"schemaVersion": 4`
- **THEN** the cup file is refused with the same message shape as a map file
