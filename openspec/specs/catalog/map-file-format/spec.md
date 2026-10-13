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

### Requirement: The boost configuration accepts two optional fields
**Priority:** MoSCoW Must. THE SYSTEM SHALL accept `boostConfig.rocketsPerRacer`, an integer from 1 to 64, and
`boostConfig.maxSpeedBlocksPerTick`, a finite number greater than zero, in addition to the two required fields
`burnDurationTicks` and `cooldownTicks`, which keep their meaning and their required status.

#### Scenario: A boost configuration with only the required fields
- **WHEN** a map's `boostConfig` states `burnDurationTicks` and `cooldownTicks` and no other field
- **THEN** the file loads, with 64 rockets and no speed cap

#### Scenario: A boost configuration without its required fields
- **WHEN** a map's `boostConfig` omits `cooldownTicks`
- **THEN** the file is refused and the message names `cooldownTicks`, as before this change

### Requirement: The map file accepts an optional ringEffects object
**Priority:** MoSCoW Must. THE SYSTEM SHALL accept a `ringEffects` object at the top level of a map file, holding the optional
numbers `boostMultiplier` and `slowMultiplier`, and SHALL read a map without it with the default factors.

#### Scenario: A map file with ringEffects in version 2
- **WHEN** a map file declares `"schemaVersion": 2` and states `ringEffects` with `"boostMultiplier": 2`
- **THEN** the file loads and its BOOST factor is 2

#### Scenario: A map file with ringEffects and no version
- **WHEN** a map file states `ringEffects` and no `schemaVersion`
- **THEN** the file loads under the version 1 rules with that BOOST factor

#### Scenario: Unknown fields inside boostConfig are still ignored
- **WHEN** a map's `boostConfig` states a field the loader does not know
- **THEN** the file loads and the unknown field is ignored, as before this change

### Requirement: A map file without the new fields loads as before
**Priority:** MoSCoW Must. THE SYSTEM SHALL load every map file that the server loaded before this change to the same
map definition, except that its BOOST and SLOW rings now carry their effects.

#### Scenario: The shipped map loads with its values
- **WHEN** the shipped map `elytraraceblueandred` is loaded
- **THEN** it has 64 rockets, no speed cap, a BOOST factor of 1.5 and a SLOW factor of 0.5

### Requirement: The guide line may name its mode, particle and colour
**MoSCoW:** Must. THE SYSTEM SHALL accept three optional fields in `guideLine`: `mode`, one of `FULL` or `WINDOW`;
`particle`, one of `END_ROD` or `DUST`; and `dustColor`, a colour written as `#` followed by six hexadecimal digits.

#### Scenario: All three fields given
- **WHEN** a map's `guideLine` names `"mode": "WINDOW"`, `"particle": "DUST"` and `"dustColor": "#3366FF"`
- **THEN** the map loads with a window mode, a dust particle and that colour

#### Scenario: None given
- **WHEN** a map's `guideLine` names none of the three fields
- **THEN** the map loads with mode `FULL`, particle `END_ROD` and colour `#FFC800`, and its rings, guide points,
  look-ahead and spacing load exactly as before

### Requirement: A file written before the guide line options loads to the same course
**MoSCoW:** Must. WHEN a map file with no `mode`, `particle` or `dustColor` is read, THE SYSTEM SHALL load the same rings,
boost tuning, guide points, look-ahead rings and particle spacing that the file states, and SHALL differ only in how the
line is drawn.

#### Scenario: Existing committed map
- **WHEN** the map file as committed before this change is read
- **THEN** its ring list, boost tuning, guide points, look-ahead and particle spacing are equal to those it had before

#### Scenario: Older server reads a newer file
- **WHEN** a server that predates these fields reads a map file that names them
- **THEN** it ignores them, as it ignores every unknown field, and draws the line in its previous way

### Requirement: An unknown mode, particle or colour is refused
**MoSCoW:** Must. IF `mode` names a value other than `FULL` or `WINDOW`, or `particle` names a value other than `END_ROD` or
`DUST`, or `dustColor` is not `#` followed by six hexadecimal digits, THEN THE SYSTEM SHALL refuse the file and name the
field and the value it found.

#### Scenario: Misspelt particle
- **WHEN** a map's `particle` is `"END-ROD"`
- **THEN** the file is refused and the refusal names `guideLine.particle` and the value `END-ROD`

#### Scenario: Malformed colour
- **WHEN** a map's `dustColor` is `"blue"`
- **THEN** the file is refused and the refusal names `guideLine.dustColor`

### Requirement: A colour is refused when the particle is not dust
**MoSCoW:** Must. IF `dustColor` is given and `particle` is not `DUST`, THEN THE SYSTEM SHALL refuse the file and name both
fields, because the colour would have no effect.

#### Scenario: Colour on end rod
- **WHEN** a map names `"particle": "END_ROD"` and `"dustColor": "#3366FF"`
- **THEN** the file is refused and the refusal names `guideLine.dustColor` and `guideLine.particle`

### Requirement: The spacing and look-ahead rules do not change
**MoSCoW:** Must. THE SYSTEM SHALL keep the refusals of `lookAheadRings` below 1 and of `particleSpacing` below 0.25 as
they are, and SHALL keep `lookAheadRings` and `particleSpacing` required, whatever the mode.

#### Scenario: Look-ahead still required in full mode
- **WHEN** a `FULL` map omits `lookAheadRings`
- **THEN** the file is refused as it is today
