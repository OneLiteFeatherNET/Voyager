## ADDED Requirements

### Requirement: Every catalogue and world problem is reported in one pass
Priority: Must. When the validation runs, the system SHALL check every map, every cup and every referenced world, and SHALL report every problem it finds in one result. It SHALL NOT stop at the first problem. The catalogue problems come from the loader's reading (`CatalogLoader.read`, change `unify-catalog-loading`). Basis: NFR-007, research 005 P10.

#### Scenario: two independent problems are both reported
- **WHEN** the maps directory holds one malformed file and one map names a world folder that does not exist
- **THEN** the result contains a problem for the malformed file and a problem for the missing world, in the same result

#### Scenario: a valid configuration yields no errors
- **WHEN** every map parses, every cup names only existing maps, and every map's world folder holds region data that is sound
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
Priority: Must. A map's world SHALL be reported as missing when its folder does not exist under the worlds directory, and as empty when the folder exists but holds no region data for the layout the server reads (`region/` or `dimensions/`, as `MapInstances` reads them). The deep check of the next requirement SHALL NOT run for a world that fails this check.

#### Scenario: an existing folder without region data is an error
- **WHEN** the world folder exists but its `region/` directory is absent or holds no `.mca` file
- **THEN** the problem states that the world holds no region data, and names the directory it looked in

#### Scenario: the dimensions layout is accepted
- **WHEN** the world folder holds a `dimensions/` tree with region data and no top-level `region/` directory
- **THEN** the folder check does not report the world

### Requirement: Every referenced world is checked for soundness
Priority: Must. Every map's world that passes the folder check SHALL be loaded through the server's own world loader, and its `WorldHealth` SHALL be read. A world whose health is not sound (`WorldHealth.isSound()` is false) SHALL be reported as an error, and the problem SHALL name the counters that failed, as `WorldHealth.describe()` states them. The check needs no game client and no connected player.

#### Scenario: a world with refused chunks is an error
- **WHEN** a referenced world reports chunks refused for their data version
- **THEN** the result contains an error for that world naming the refused chunks and the versions

#### Scenario: a world with no chunk read is an error
- **WHEN** a referenced world reports that no chunk was read at all
- **THEN** the result contains an error for that world stating that no chunk was read

#### Scenario: a sound world is not reported
- **WHEN** a referenced world reports at least one chunk read, no unknown block, no refused chunk and no failed chunk
- **THEN** the result contains no problem for that world

#### Scenario: one failing world does not hide another
- **WHEN** two worlds are referenced and the first one throws while it is checked
- **THEN** the result contains an error for the first world with the exception's message, and the second world is still checked

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
