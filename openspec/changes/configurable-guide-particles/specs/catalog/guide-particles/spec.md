## ADDED Requirements

### Requirement: Every shipped map names its guide particle
**MoSCoW:** Must. THE SYSTEM SHALL ship every map in the shipped catalogue with an explicit `particle` in its `guideLine`, one of
`END_ROD` or `DUST`. WHEN the particle is `DUST`, THE SYSTEM SHALL also ship a `dustColor` for the map.

#### Scenario: A shipped map names both fields
- **WHEN** the shipped catalogue is read
- **THEN** each map's `guideLine` names a `particle`, and each `DUST` map names a `dustColor`

#### Scenario: A shipped map with no particle
- **WHEN** a shipped map's `guideLine` names no `particle`
- **THEN** the shipped-catalogue test fails and names the map

### Requirement: A map that names no particle is drawn as END_ROD
**MoSCoW:** Must. WHEN a map's `guideLine` names no `particle`, THE SYSTEM SHALL draw its line as `END_ROD`, as
`catalog/map-file-format` states. This default is not changed by this change.

#### Scenario: A map without the field
- **WHEN** a map that names no particle is loaded
- **THEN** its line is drawn as `END_ROD`, and its other fields load as before

### Requirement: Saving a loaded map keeps its guide particle and colour
**MoSCoW:** Should. WHEN the setup server saves a map it loaded, THE SYSTEM SHALL write the map's `particle` and, for `DUST`, its
`dustColor`, unchanged.

#### Scenario: A save with DUST
- **WHEN** a map that names `DUST` and `#7A1FB8` is loaded and saved by the setup server
- **THEN** the saved file names `DUST` and `#7A1FB8`
