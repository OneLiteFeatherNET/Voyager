# Spec Delta

## Purpose

A map or draft file states an oval ring with two optional fields, `radiusMinor` and `majorAxis`, which must come together.
A file that states an oval needs schema version 3, so a server that reads only up to version 2 refuses the file instead of
reading the oval as a circle. A circle-only file keeps its bytes and its layout version.

## ADDED Requirements

### Requirement: An oval is stated by a pair of fields
**Priority:** MoSCoW Must

WHEN a ring object states `radiusMinor`, THE SYSTEM SHALL require `majorAxis` as well, and WHEN a ring object states
`majorAxis`, THE SYSTEM SHALL require `radiusMinor` as well. IF only one of the two fields is stated, THEN THE SYSTEM SHALL
refuse the file, and the message SHALL name the file, the ring's position and the missing field. In an oval ring, `radius`
SHALL be the semi-major radius.

#### Scenario: Minor radius without major axis
- **WHEN** the third ring of a map file states `radiusMinor` and no `majorAxis`
- **THEN** the file is refused, and the message names the file, position 2 and `majorAxis`

#### Scenario: Major axis without minor radius
- **WHEN** the first ring of a map file states `majorAxis` and no `radiusMinor`
- **THEN** the file is refused, and the message names the file, position 0 and `radiusMinor`

#### Scenario: Complete pair reads as an oval
- **WHEN** a version 3 ring states `radius` 6, `radiusMinor` 3 and a unit `majorAxis` perpendicular to its `normal`
- **THEN** the ring is an oval with semi-major radius 6 and semi-minor radius 3

### Requirement: An oval needs schema version 3
**Priority:** MoSCoW Must

IF a map or cup file states a ring with `radiusMinor` or `majorAxis`, and the file's `schemaVersion` is below 3 or is absent,
THEN THE SYSTEM SHALL refuse the file, and the message SHALL name the file, the ring's position, the field and the version 3.

#### Scenario: Version 2 file with an oval
- **WHEN** a map file states `"schemaVersion": 2` and a ring with `radiusMinor`
- **THEN** the file is refused, and the message names the file, the ring's position, `radiusMinor` and version 3

#### Scenario: Unversioned file with an oval
- **WHEN** a map file states no `schemaVersion` and a ring with `majorAxis` and `radiusMinor`
- **THEN** the file is refused, and the message names the file, the ring's position, the field and version 3

#### Scenario: Version 3 file with an oval
- **WHEN** a map file states `"schemaVersion": 3` and a ring with `radiusMinor` and `majorAxis`
- **THEN** the file loads and that ring is an oval

### Requirement: A version 3 file loads with or without an oval
**Priority:** MoSCoW Must

WHEN a map or cup file states `"schemaVersion": 3`, THE SYSTEM SHALL load the file under the version 3 rules, including a file
whose rings are all circles.

#### Scenario: Version 3 file of circles
- **WHEN** a map file states `"schemaVersion": 3` and every ring has `radius` and no oval field
- **THEN** the file loads, and every ring is a circle

### Requirement: An invalid oval in a file names the file, the ring and the field
**Priority:** MoSCoW Must

IF an oval ring in a file has a `radiusMinor` that is not greater than zero or greater than `radius`, or a `majorAxis` that is not
of unit length or not perpendicular to `normal`, THEN THE SYSTEM SHALL refuse the file, and the message SHALL name the file,
the ring's position and the field that is wrong.

#### Scenario: Minor radius above the major radius in a file
- **WHEN** the second ring of a version 3 map file states `radius` 6 and `radiusMinor` 7
- **THEN** the file is refused, and the message names the file, position 1 and `radiusMinor`

#### Scenario: Major axis parallel to the normal in a file
- **WHEN** the first ring of a version 3 map file states a `majorAxis` equal to its `normal`
- **THEN** the file is refused, and the message names the file, position 0 and `majorAxis`

### Requirement: Equal radii in a file make a circle
**Priority:** MoSCoW Must

WHEN a version 3 ring states a `radiusMinor` equal to its `radius`, THE SYSTEM SHALL read the ring as a circle, and SHALL still
check the `majorAxis` as stated.

#### Scenario: Equal radii read as a circle
- **WHEN** a version 3 ring states `radius` 4, `radiusMinor` 4 and a valid `majorAxis`
- **THEN** the ring is a circle of radius 4

### Requirement: The layout hash changes only for a map with an oval
**Priority:** MoSCoW Must

THE SYSTEM SHALL write `radiusMinor` and `majorAxis` into the canonical geometry form only for an oval ring. A map whose rings
are all circles SHALL keep the canonical form and the layout version it has before this change, and a map with an oval SHALL
have a different canonical form.

#### Scenario: Circle-only map keeps its layout version
- **WHEN** a shipped map with circular rings is canonicalised before and after this change
- **THEN** the two canonical forms are equal and the layout version is the same

#### Scenario: Map with an oval has its own layout version
- **WHEN** a map has an oval ring and is canonicalised
- **THEN** its canonical form holds `radiusMinor` and `majorAxis` for that ring, and its layout version differs from the layout version of the same map with that ring as a circle

### Requirement: The shipped catalogue has no oval in this change
**Priority:** MoSCoW Must

THE SYSTEM SHALL NOT change any shipped map or cup to contain an oval ring in this change, so the shipped catalogue keeps its
schema version and its layout versions.

#### Scenario: Shipped maps after this change
- **WHEN** the shipped catalogue is loaded after this change
- **THEN** no ring of any shipped map is an oval
