## ADDED Requirements

### Requirement: Every catalogue and world problem is reported in one pass
Priority: Must. When the validation runs, the system SHALL check every map, every cup and every referenced world, and SHALL report every problem it finds in one result. It SHALL NOT stop at the first problem. Basis: NFR-007, research 005 P10.

#### Scenario: two independent problems are both reported
- **WHEN** the maps directory holds one malformed file and one map names a world folder that does not exist
- **THEN** the result contains a problem for the malformed file and a problem for the missing world, in the same result

#### Scenario: a valid configuration yields no errors
- **WHEN** every map parses, every cup names only existing maps, and every map's world folder holds region data
- **THEN** the result contains no error problems

### Requirement: Each problem names its file and field
Priority: Must. Each problem SHALL carry a source (the file path, or the setting name for a setting) and a key (the JSON field, or the setting name), and a message that says what is wrong. Paths SHALL be absolute.

#### Scenario: a missing world names the map file and the world field
- **WHEN** a map file `maps/blue.json` declares `world` as `ElytraraceMissing`, and no folder of that name exists under the worlds directory
- **THEN** the problem has source `<absolute path>/maps/blue.json`, key `world`, and a message naming the absolute path that was looked up

#### Scenario: an unknown cup map names the cup file
- **WHEN** a cup file plays a map name that no map file provides
- **THEN** the problem has the cup file as source and the map entry as key

### Requirement: A world folder counts as present only with region data
Priority: Must. A map's world SHALL be reported as missing when its folder does not exist under the worlds directory, and as empty when the folder exists but holds no region data for the layout the server reads (`region/` or `dimensions/`, as `MapInstances` reads them).

#### Scenario: an existing folder without region data is an error
- **WHEN** the world folder exists but its `region/` directory is absent or holds no `.mca` file
- **THEN** the problem states that the world holds no region data, and names the directory it looked in

#### Scenario: the dimensions layout is accepted
- **WHEN** the world folder holds a `dimensions/` tree with region data and no top-level `region/` directory
- **THEN** the world is not reported

### Requirement: Results are deterministic
Priority: Must. The problems SHALL be sorted by source, then by key, and two runs over the same directories SHALL produce the same lines in the same order. Files SHALL be read in sorted filename order.

#### Scenario: repeated runs print identical output
- **WHEN** the validation runs twice over the same fixture directory
- **THEN** both results are equal, element for element

### Requirement: Warnings do not fail the validation
Priority: Should. A problem SHALL carry a severity of error or warning. Only errors SHALL cause a failing result. A cup that is not selected and has a warning-level problem SHALL NOT fail the result.

#### Scenario: a warning alone leaves the result passing
- **WHEN** the only problem is a warning-level problem
- **THEN** the result has no errors and the warning is still listed

### Requirement: The deep world check reads region data through the loader
Priority: Could. Where the owner enables the deep check, the system SHALL load each referenced world through the same loader the server uses and SHALL report a world whose health is not sound, using the `WorldHealth` verdict. Without the deep check, no chunk is read.

#### Scenario: the shallow check reads no chunk
- **WHEN** the deep check is not enabled
- **THEN** the validation does not open any region file

#### Scenario: a world with refused chunks is an error when deep
- **WHEN** the deep check is enabled and a world reports refused chunks
- **THEN** the result contains an error for that world naming the refused versions
