# setup/map-draft-storage Specification

## Purpose
A map under construction is a draft on the setup server. A draft is stored where the game server reads it once the game can
load it, and in a folder the game never reads until then. Both files use the field names and value shapes of the game
server's map format, so no publish step or conversion exists. The game server's catalog reads flat `*.json` files under
`maps/` and nothing else, so an incomplete draft cannot break a game boot.

## Requirements

### Requirement: A draft lives where its completeness puts it
**Priority:** MoSCoW Must

THE SYSTEM SHALL store a draft with at least one ring and a spawn as `<dataPath>/maps/<id>.json`, and every other draft as
`<dataPath>/drafts/<id>.json`. THE SYSTEM SHALL keep the map's world at `<worldsPath>/<id>`, in both cases, and SHALL write
`world` as `<id>` explicitly.

#### Scenario: New map has no ring and no spawn
- **WHEN** a map `skyfortress` is created
- **THEN** the file `drafts/skyfortress.json` exists, `maps/skyfortress.json` does not, and the world folder is `<worldsPath>/skyfortress`

#### Scenario: Ring and spawn make the draft game-loadable
- **WHEN** a draft with a spawn and one ring is saved
- **THEN** `maps/skyfortress.json` holds the draft, and `drafts/skyfortress.json` no longer exists

#### Scenario: Last ring removed
- **WHEN** the only ring of a game-loadable draft is removed and the draft is saved
- **THEN** the file moves to `drafts/skyfortress.json` and `maps/` holds no file for the map

#### Scenario: Game server catalog ignores drafts
- **WHEN** the game server reads its `maps/` directory and a draft exists only under `drafts/`
- **THEN** the draft is not read, because the game reads only the `maps/` directory

### Requirement: The draft JSON uses the game server's map format
**Priority:** MoSCoW Must

THE SYSTEM SHALL write the draft with `schemaVersion` as `2` first, or as `3` when one of its rings is an oval, then the keys
`key`, `world`, `displayName` (only when set), `authors` (only when set), `spawn`, `referenceTimeSeconds`, `boostConfig`,
`guideLine` and `rings`, and each ring with the keys `index`, `center`, `normal`, `radius`, `points` and `type`, followed, for an
oval ring only, by `radiusMinor` and `majorAxis`, using the same value shapes as the shipped map. The `key` SHALL be
`elytrarace:` followed by the map's folder name. `index` SHALL always be written. WHILE no spawn is set, THE SYSTEM SHALL omit
the `spawn` key.

#### Scenario: Draft with one ring and a spawn
- **WHEN** a draft with a spawn and one ring is saved
- **THEN** the file has every key listed above, `schemaVersion` is 2, `key` is `elytrarace:<folder name>`, the ring's `index` is 0 and its `type` is the string `STANDARD`

#### Scenario: Draft without spawn
- **WHEN** a draft without spawn is saved
- **THEN** the file has no `spawn` key

#### Scenario: A save keeps the display name and the authors
- **WHEN** a map that has `displayName` "Blue & Red" and two authors is loaded into the setup server and saved without edits
- **THEN** the saved file has the same `displayName` and the same two authors

#### Scenario: A map without display name or authors writes neither key
- **WHEN** a map with no `displayName` and no `authors` is saved
- **THEN** the file has no `displayName` and no `authors` key

#### Scenario: Draft with an oval ring
- **WHEN** a draft with a spawn and one oval ring of `radius` 6 and `radiusMinor` 3 is saved
- **THEN** the file's `schemaVersion` is 3, the ring has `radius` 6, `radiusMinor` 3 and the `majorAxis` of the oval, and the file loads again as the same oval

#### Scenario: Draft of circles stays at version 2
- **WHEN** a draft whose rings are all circles is saved
- **THEN** the file's `schemaVersion` is 2 and the ring objects have no `radiusMinor` or `majorAxis` key

### Requirement: A game-loadable draft is readable by the game server's map adapter
**Priority:** MoSCoW Must

WHEN a draft has a spawn, at least one ring, and a valid reference time, THE SYSTEM SHALL write a file that the game server's
`MapDefinitionAdapter` reads without error, and this SHALL be verified by a test in `voyager-platform`.

#### Scenario: Round trip
- **WHEN** a complete draft is written and then read by the game's map adapter
- **THEN** the read map equals the draft's fields, ring for ring

### Requirement: Saves replace the file atomically
**Priority:** MoSCoW Must

WHEN a draft is saved, THE SYSTEM SHALL write the new content to a temporary file in the target folder, force it to disk, and
move it over the target file as an atomic replace. THE SYSTEM SHALL NOT delete the file being saved before the new content exists.

#### Scenario: Save over an existing file
- **WHEN** a game-loadable draft is saved and `maps/<id>.json` already exists
- **THEN** no moment exists at which `maps/<id>.json` is absent, and the file holds the new content afterwards

### Requirement: A save removes the stale copy in the other folder
**Priority:** MoSCoW Must

WHEN a save moves a draft from one folder to the other, THE SYSTEM SHALL delete the copy in the folder it left after the new
copy exists.

#### Scenario: First ring placed
- **WHEN** the first ring and the spawn of `skyfortress` are saved
- **THEN** `maps/skyfortress.json` exists and `drafts/skyfortress.json` does not

### Requirement: Two copies of one draft are refused, never resolved silently
**Priority:** MoSCoW Must

IF a draft exists in both `maps/` and `drafts/`, THEN THE SYSTEM SHALL refuse to open or load it with a message naming both files,
and SHALL NOT choose one of them.

#### Scenario: Crash left two copies
- **WHEN** `/map open skyfortress` runs while `maps/skyfortress.json` and `drafts/skyfortress.json` both exist
- **THEN** the server refuses, the message names both files, and neither file changes

### Requirement: A failed save leaves the previous draft intact
**Priority:** MoSCoW Must

IF writing the temporary file or moving it fails, THEN THE SYSTEM SHALL keep the previous draft file unchanged, SHALL delete
only its own temporary file, and SHALL report the failure to the caller.

#### Scenario: Move fails
- **WHEN** the atomic move throws
- **THEN** the target file has its previous bytes, no temporary file remains, and the caller receives a draft-write failure

### Requirement: Map ids and draft invariants are checked before writing
**Priority:** MoSCoW Must

THE SYSTEM SHALL refuse to write a draft whose rings do not have index equal to list position, whose name is not the map id,
or whose world is blank.

#### Scenario: Ring index out of place
- **WHEN** a draft holds a ring with index 3 at list position 2
- **THEN** the write is refused with an invalid-draft exception and no file changes

### Requirement: A save refuses to replace a draft that changed on disk
**Priority:** MoSCoW Must

WHEN a session saves a draft, THE SYSTEM SHALL compare the stored copy with the draft the session last loaded or saved, by
their canonical JSON, and IF the stored copy differs or is missing, THEN THE SYSTEM SHALL refuse the save with a
draft-changed failure and SHALL leave the stored copy unchanged.

#### Scenario: Second setup server changed the draft
- **WHEN** one setup server saves a changed `skyfortress` draft, and a second session that still holds the earlier draft saves an edit
- **THEN** the second save is refused with a draft-changed failure, and the file keeps the bytes of the first save

#### Scenario: Stored copy differs only in layout
- **WHEN** the stored copy of `skyfortress` has the same values as the session's draft but other whitespace and key order
- **THEN** the save is accepted and the file is written in the setup server's layout

#### Scenario: Stored copy is missing
- **WHEN** a session saves a draft whose stored copy was deleted from both folders
- **THEN** the save is refused with a draft-changed failure and no file is created

#### Scenario: Refused save keeps the session's draft
- **WHEN** a save is refused with a draft-changed failure
- **THEN** the session keeps the draft it had before the edit, and the builder is told that the map changed on disk
