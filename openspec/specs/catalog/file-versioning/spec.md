# catalog/file-versioning Specification

## Purpose
Defines the `schemaVersion` field of map and cup files. The field lets the format change later without guessing which
rules an old file was written under, and it lets an older server refuse a newer file plainly rather than misread it.

## Requirements

### Requirement: A file without a schema version is version 1
**Priority:** MoSCoW Must

WHEN a map or cup file omits `schemaVersion`, THE SYSTEM SHALL read the file as schema version 1.

#### Scenario: Map without schemaVersion
- **WHEN** a map file has no `schemaVersion` field
- **THEN** the file loads under the version 1 rules and is not refused for that reason

#### Scenario: Cup without schemaVersion
- **WHEN** a cup file has no `schemaVersion` field
- **THEN** the file loads under the version 1 rules and is not refused for that reason

### Requirement: The schema version must be a whole number of at least 1
**Priority:** MoSCoW Must

WHEN a map or cup file states `schemaVersion` as anything other than a whole number of 1 or more, THE SYSTEM SHALL refuse
that file and name the file and the field.

#### Scenario: Zero version
- **WHEN** a file states `"schemaVersion": 0`
- **THEN** the file is refused and the message names the file and the field `schemaVersion`

#### Scenario: Fractional version
- **WHEN** a file states `"schemaVersion": 1.5`
- **THEN** the file is refused and the message names the file and the field `schemaVersion`

#### Scenario: Text version
- **WHEN** a file states `"schemaVersion": "1"`
- **THEN** the file is refused and the message names the file and the field `schemaVersion`

### Requirement: A newer schema version is refused with the supported maximum
**Priority:** MoSCoW Must

WHEN a map or cup file states a `schemaVersion` above the highest version this server reads, THE SYSTEM SHALL refuse
that file, and the message SHALL name the file, the declared version and the highest supported version.

#### Scenario: Map written for a future format
- **WHEN** a map file states `"schemaVersion": 2` and this server reads up to version 1
- **THEN** the file is refused and the message names the file, `2` and `1`

#### Scenario: Refusal loads nothing from the file
- **WHEN** a map file with `schemaVersion` 2 is refused
- **THEN** no ring, guide point or world from that file reaches the catalogue

### Requirement: The rule applies to maps and cups alike
**Priority:** MoSCoW Must

THE SYSTEM SHALL apply the same schema version rule to map files and cup files, with the same messages.

#### Scenario: Cup written for a future format
- **WHEN** a cup file states `"schemaVersion": 2`
- **THEN** the cup file is refused with the same message shape as a map file

### Requirement: Files written by the converter declare version 1
**Priority:** MoSCoW Should

THE SYSTEM SHALL have `tools/map-converter` write `"schemaVersion": 1` in every map and cup file it produces.

#### Scenario: Converted map declares its version
- **WHEN** the converter writes a map file
- **THEN** the file contains `"schemaVersion": 1`
