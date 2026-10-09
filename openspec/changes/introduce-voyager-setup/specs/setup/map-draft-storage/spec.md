# Spec Delta

## Purpose

A map under construction is a draft folder on the setup server. Its `map.json` uses the field names and value shapes of
the game server's map format, so a finished draft can be published without conversion. The game server's flat catalog
skips folders, so drafts are not loaded by the game in this change.

## ADDED Requirements

### Requirement: Drafts live in one folder per map
**Priority:** MoSCoW Must

THE SYSTEM SHALL store each draft as `maps/<id>/map.json` with its world directory at `maps/<id>/world/`, where the draft's
world value is `world`, relative to that folder.

#### Scenario: Layout of a new draft
- **WHEN** a map `skyfortress` is created
- **THEN** the files are `maps/skyfortress/map.json` and `maps/skyfortress/world/`, and `map.json` names the world `world`

#### Scenario: Game server catalog ignores drafts
- **WHEN** the game server reads its `maps/` directory and a folder `maps/skyfortress/` exists
- **THEN** the folder is not read as a map definition, because the catalog only reads regular files ending in `.json`

### Requirement: The draft JSON uses the game server's map format
**Priority:** MoSCoW Must

THE SYSTEM SHALL write `map.json` with the keys `name`, `world`, `spawn`, `referenceTimeSeconds`, `boostConfig`,
`guideLine` and `rings`, and each ring with the keys `index`, `center`, `normal`, `radius`, `points` and `type`, using the
same value shapes as the shipped map. WHILE no spawn is set, THE SYSTEM SHALL omit the `spawn` key.

#### Scenario: Draft with one ring and a spawn
- **WHEN** a draft with a spawn and one ring is saved
- **THEN** the file has every key listed above and the ring's `type` is the string `STANDARD`

#### Scenario: Draft without spawn
- **WHEN** a draft without spawn is saved
- **THEN** the file has no `spawn` key

### Requirement: A completed draft is readable by the game server's map adapter
**Priority:** MoSCoW Must

WHEN a draft has a spawn, at least one ring, and a valid reference time, THE SYSTEM SHALL write a file that the game server's
`MapDefinitionAdapter` reads without error, and this SHALL be verified by a test in `voyager-platform`.

#### Scenario: Round trip
- **WHEN** a complete draft is written and then read by the game's map adapter
- **THEN** the read map equals the draft's fields, ring for ring

### Requirement: Saves replace the file atomically
**Priority:** MoSCoW Must

WHEN a draft is saved, THE SYSTEM SHALL write the new content to a temporary file in the same folder, force it to disk, and
move it over `map.json` as an atomic replace. THE SYSTEM SHALL NOT delete `map.json` before the new content exists.

#### Scenario: Save over an existing file
- **WHEN** a draft is saved and `map.json` already exists
- **THEN** no moment exists at which `map.json` is absent, and the file holds the new content afterwards

### Requirement: A failed save leaves the previous draft intact
**Priority:** MoSCoW Must

IF writing the temporary file or moving it fails, THEN THE SYSTEM SHALL keep the previous `map.json` unchanged, SHALL delete
only its own temporary file, and SHALL report the failure to the caller.

#### Scenario: Move fails
- **WHEN** the atomic move throws
- **THEN** `map.json` has its previous bytes, no temporary file remains, and the caller receives a draft-write failure

### Requirement: Map ids and draft invariants are checked before writing
**Priority:** MoSCoW Must

THE SYSTEM SHALL refuse to write a draft whose rings do not have index equal to list position, whose name is not the map id,
or whose world is blank.

#### Scenario: Ring index out of place
- **WHEN** a draft holds a ring with index 3 at list position 2
- **THEN** the write is refused with an invalid-draft exception and no file changes
