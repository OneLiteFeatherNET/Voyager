# Delta for catalog/map-file-format

Adds three optional fields to the racing line's `guideLine` block. Nothing that loads today stops loading, and no
`schemaVersion` changes. The required fields, their refusals and the rest of the format keep their current text.

## ADDED Requirements

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
