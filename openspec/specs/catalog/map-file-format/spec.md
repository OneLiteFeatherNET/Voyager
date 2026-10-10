# catalog/map-file-format Specification

## Purpose
Defines which fields a map definition file must state and which the loader derives. A ring's index and a map's
world are derived from data already in the file when the author leaves them out; a derived value that contradicts
the file is refused, never silently corrected.

## Requirements

### Requirement: A ring's index defaults to its position
**Priority:** MoSCoW Must

WHEN a ring object in a map file omits `index`, THE SYSTEM SHALL assign that ring the zero-based position of the
ring in the map's `rings` array.

#### Scenario: Ring without an index
- **WHEN** a map file lists rings without any `index` field
- **THEN** ring 0 is the first listed ring, ring 1 the second, and so on through the last ring

#### Scenario: Index-less and indexed files are the same course
- **WHEN** the committed map file is loaded once as shipped and once with every `index` field removed
- **THEN** both loads produce equal `MapDefinition` values

### Requirement: A declared ring index must match its position
**Priority:** MoSCoW Must

WHEN a ring object declares an `index` that differs from its zero-based position in the `rings` array, THE SYSTEM
SHALL refuse the map file and name the file, the ring's position and the declared index.

#### Scenario: Index contradicts position
- **WHEN** the fourth ring of a map file declares `"index": 5`
- **THEN** the map file is refused, and the message names the file, position 3 and the declared index 5

#### Scenario: Index matches position
- **WHEN** the fourth ring declares `"index": 3`
- **THEN** the file loads and that ring has index 3

### Requirement: A map's world defaults to its name
**Priority:** MoSCoW Must

WHEN a map file omits `world`, THE SYSTEM SHALL use the map's `name` exactly as written, with the same characters and
the same letter case, as the world directory name.

#### Scenario: World omitted
- **WHEN** a map file named `sky-drift` has no `world` field
- **THEN** the loaded map's world is `sky-drift`

#### Scenario: Derived world is not case-folded
- **WHEN** a map file named `elytraraceblueandred` has no `world` field
- **THEN** the loaded map's world is `elytraraceblueandred`, not `ElytraraceBlueAndRed`

### Requirement: An explicit world is kept as written
**Priority:** MoSCoW Must

WHEN a map file states `world`, THE SYSTEM SHALL use that value unchanged, even when it differs from the map's name.

#### Scenario: Shipped map keeps its world directory
- **WHEN** `elytraraceblueandred.json` states `"world": "ElytraraceBlueAndRed"`
- **THEN** the loaded map's world is `ElytraraceBlueAndRed`

#### Scenario: Blank explicit world
- **WHEN** a map file states `"world": "  "`
- **THEN** the file is refused, as today, and the message names the file and the field

### Requirement: A derived world without a directory fails at boot
**Priority:** MoSCoW Must

WHEN a map's resolved world has no region data on disk, THE SYSTEM SHALL keep refusing to start, and the refusal SHALL
name the resolved world value rather than the field that produced it.

#### Scenario: Derived world directory missing
- **WHEN** a map omits `world`, its name is `sky-drift`, and no `sky-drift` directory exists under the worlds path
- **THEN** startup fails with an error naming world `sky-drift`

### Requirement: Derivation never changes a file that loads today
**Priority:** MoSCoW Must

THE SYSTEM SHALL load every map file that is valid before this change to a `MapDefinition` equal to the one it produced
before this change.

#### Scenario: Existing shipped map
- **WHEN** the shipped `elytraraceblueandred.json` is loaded after this change
- **THEN** every field of the resulting `MapDefinition` equals the value loaded before this change

### Requirement: The converter states what it used to derive
**Priority:** MoSCoW Should

THE SYSTEM SHALL have `tools/map-converter` write every ring's `index` and the map's `world` explicitly, so converted
files do not depend on derivation rules to be read correctly.

#### Scenario: Converted ring carries its index
- **WHEN** the converter writes a course of 35 rings
- **THEN** each ring object in the output carries an `index` equal to its position

#### Scenario: Converted map carries its world
- **WHEN** the converter writes a map whose legacy world directory is `ElytraraceBlueAndRed`
- **THEN** the output carries `"world": "ElytraraceBlueAndRed"`
