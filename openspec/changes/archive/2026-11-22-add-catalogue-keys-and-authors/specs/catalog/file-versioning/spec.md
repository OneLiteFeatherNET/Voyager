# Spec Delta

## Purpose

The highest schema version this server reads becomes 2, the version that states a `key`. The refusal of a newer version
is unchanged in its rule and moves its examples to version 3.

## MODIFIED Requirements

### Requirement: A newer schema version is refused with the supported maximum
**Priority:** MoSCoW Must

WHEN a map or cup file states a `schemaVersion` above the highest version this server reads, THE SYSTEM SHALL refuse
that file, and the message SHALL name the file, the declared version and the highest supported version. The highest
version this server reads is 2.

#### Scenario: Map written for a future format
- **WHEN** a map file states `"schemaVersion": 3` and this server reads up to version 2
- **THEN** the file is refused and the message names the file, `3` and `2`

#### Scenario: Refusal loads nothing from the file
- **WHEN** a map file with `schemaVersion` 3 is refused
- **THEN** no ring, guide point, world or key from that file reaches the catalogue
