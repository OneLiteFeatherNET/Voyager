# Spec Delta

## Purpose

The setup server writes a cup to the same folder and in the same format the game server reads, so that the file a builder saves
is the file the operator copies to the game server, with no conversion. The save is atomic, a save of an edited cup keeps what the
setup server does not edit, and the setup server never writes a cup the game would refuse.

## ADDED Requirements

### Requirement: A cup is written as a version 2 file with its key
**Priority:** MoSCoW Must

THE SYSTEM SHALL write a cup as a JSON object with `schemaVersion` as `2` first, then `key` as `elytrarace:` followed by the cup's id, then `displayName`
(only when the cup has one), then `mode`, then `mapNames` in play order, then `authors` (only when the cup has authors). THE SYSTEM SHALL NOT write a
`notes` key.

#### Scenario: A new cup with one map
- **WHEN** the cup `alpha_run` with mode `RACE` and the map `elytrarace:blue_and_red` is saved
- **THEN** the file holds `"schemaVersion": 2`, `"key": "elytrarace:alpha_run"`, `"mode": "RACE"` and `"mapNames": ["elytrarace:blue_and_red"]`, and holds no `displayName` and no `authors` key

#### Scenario: The file is read by the game's cup adapter
- **WHEN** a cup saved by the setup server is read by the game server's cup adapter
- **THEN** the read cup has the same key, mode, map list, display name and authors as the saved one

### Requirement: A cup file lives in the cups folder of the setup data directory
**Priority:** MoSCoW Must

THE SYSTEM SHALL write a cup to `<dataPath>/cups/<id>.json`, the folder the game server reads cups from, and SHALL create the `cups` folder when it does not exist.

#### Scenario: First cup in a new data directory
- **WHEN** the first cup is saved and `<dataPath>/cups` does not exist
- **THEN** the folder is created and the file `<dataPath>/cups/<id>.json` holds the cup

### Requirement: A cup id is a safe file name
**Priority:** MoSCoW Must

THE SYSTEM SHALL accept a cup id only if it matches `[a-z0-9][a-z0-9_-]{0,31}`, the same rule as a map id, and SHALL refuse any other id before the file system is touched.

#### Scenario: Path traversal attempt
- **WHEN** a builder runs `/cup new ../../etc RACE blue_and_red`
- **THEN** the reply names the id rule and no file or folder is created

### Requirement: A cup save replaces the file atomically
**Priority:** MoSCoW Must

WHEN a cup is saved, THE SYSTEM SHALL write the new content to a temporary file in the `cups` folder, force it to disk, and move it over the target as an atomic replace. IF the write or the move fails, THEN THE SYSTEM SHALL keep the previous file unchanged, SHALL delete only its own temporary file, and SHALL report a write failure to the caller.

#### Scenario: Move fails
- **WHEN** the atomic move throws while saving an edited cup
- **THEN** the cup file holds its previous bytes, no temporary file remains, and the caller receives a cup-write failure

### Requirement: The save checks each map against the game's map catalogue
**Priority:** MoSCoW Must

WHEN a cup is saved, THE SYSTEM SHALL resolve each name in `mapNames` against the maps the game can load from `<dataPath>/maps/`, using the catalogue's cross-check rule, and IF a name does not resolve, THEN THE SYSTEM SHALL refuse the save with an exception that lists every name that does not resolve, and SHALL write nothing.

#### Scenario: Two unresolved names
- **WHEN** a cup naming `elytrarace:missing_a` and `elytrarace:missing_b` is saved
- **THEN** the save is refused, the exception lists both keys, and no file changes

### Requirement: A cup save is refused when the stored copy changed since the command read it
**Priority:** MoSCoW Must

WHEN a `/cup` command saves a cup, THE SYSTEM SHALL compare the stored file with the cup the same command read, and IF the stored copy
differs, THEN THE SYSTEM SHALL refuse the save, SHALL leave the stored file unchanged, and SHALL tell the builder that the cup changed
on disk. The comparison is made on canonical JSON, not on the bytes of the file.

#### Scenario: An outside edit between read and save
- **WHEN** a `/cup map add` command has read `alpha_run.json`, another process rewrites that file with a different mode, and the command then saves
- **THEN** the save is refused, the file keeps the other process's content, and the builder is told the cup changed on disk

#### Scenario: A hand-edited file with the same values
- **WHEN** a builder runs `/cup name alpha_run Alpha` on a file whose values are the same as the writer's but whose keys are in another order
- **THEN** the save is accepted and the file holds the canonical form with the new name

### Requirement: An edit keeps the display name and the authors the cup already has
**Priority:** MoSCoW Must

WHEN an existing cup is loaded and saved after an edit of its mode or its maps, THE SYSTEM SHALL keep its display name and its authors, so that a save changes only what the builder edited.

#### Scenario: Mode edit keeps the authors
- **WHEN** a cup with the display name `Alpha Run` and one author is loaded, its mode is changed to `PRACTICE`, and it is saved
- **THEN** the saved file has the display name `Alpha Run`, the same author, and the mode `PRACTICE`

### Requirement: The setup server does not create the cups folder at boot
**Priority:** MoSCoW Should

THE SYSTEM SHALL NOT create the `cups` folder when the setup server starts; the folder is created by the first cup save, so that the boot behaviour of `setup/server-bootstrap` is unchanged.

#### Scenario: Boot without cups
- **WHEN** the setup server starts with a data directory that holds no `cups` folder
- **THEN** it starts, and the `cups` folder does not exist until a cup is saved
