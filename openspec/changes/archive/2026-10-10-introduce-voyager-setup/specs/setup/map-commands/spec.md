# Spec Delta

## Purpose

The builder creates, opens and names maps with `/map` subcommands on the setup server. A map is a draft folder, and
the commands are the only way a draft is created or opened in this change.

## ADDED Requirements

### Requirement: Creating a map creates a draft folder and a void world
**Priority:** MoSCoW Must

WHEN a builder runs `/map new <id>` with a valid id and no draft or world named `<id>` exists, THE SYSTEM SHALL create
`drafts/<id>.json` as a draft skeleton (see `setup/map-draft-storage`), SHALL create `<worldsPath>/<id>` from the void template,
SHALL open the map, and SHALL give the builder the wand.

#### Scenario: New map with a free id
- **WHEN** a builder runs `/map new skyfortress` and neither `maps/skyfortress.json`, `drafts/skyfortress.json` nor `<worldsPath>/skyfortress` exists
- **THEN** `drafts/skyfortress.json` holds name `skyfortress`, world `skyfortress`, no spawn and no rings; the folder `<worldsPath>/skyfortress` holds region data; the builder stands in the map and holds the wand

#### Scenario: Id already in use
- **WHEN** a builder runs `/map new skyfortress` and a draft or a world named `skyfortress` exists
- **THEN** the server refuses with a message naming the id, and no file or folder changes

### Requirement: Opening a map loads its draft
**Priority:** MoSCoW Must

WHEN a builder runs `/map open <id>` for an existing draft, THE SYSTEM SHALL load the draft from `maps/<id>.json` or from
`drafts/<id>.json`, SHALL open the map world at `<worldsPath>/<id>`, SHALL show the ring previews, and SHALL give the builder
the wand if the builder does not hold one.

#### Scenario: Open a draft with three rings
- **WHEN** a builder runs `/map open skyfortress` and the file holds three rings
- **THEN** the builder stands in the map, sees three previews, and the wand is in the builder's inventory

#### Scenario: Open a missing or malformed draft
- **WHEN** a builder runs `/map open <id>` and no draft exists for the id, or the draft file is not valid JSON
- **THEN** the server refuses with a message naming the file, and the builder's current map is unchanged

### Requirement: Spawn is set from the builder's position
**Priority:** MoSCoW Must

WHEN a builder runs `/map spawn` in an open map, THE SYSTEM SHALL set the draft spawn to the builder's feet position and
SHALL autosave the draft.

#### Scenario: Spawn set
- **WHEN** a builder standing at (109, -62, 54) runs `/map spawn`
- **THEN** the draft spawn is (109, -62, 54) and the draft file is saved with that spawn

### Requirement: Map ids are safe folder names
**Priority:** MoSCoW Must

THE SYSTEM SHALL accept a map id only if it matches `[a-z0-9][a-z0-9_-]{0,31}`, and SHALL refuse any other id before the
file system is touched.

#### Scenario: Path traversal attempt
- **WHEN** a builder runs `/map new ../../etc`
- **THEN** the server refuses with a message that names the rule, and no folder is created

### Requirement: A builder has one open map at a time
**Priority:** MoSCoW Must

WHEN a builder opens or creates a map while another map is open, THE SYSTEM SHALL close the first map's session after its
last autosave has succeeded and SHALL show the new map only.

#### Scenario: Switch maps
- **WHEN** a builder with `skyfortress` open runs `/map open canyon`
- **THEN** the previews of `skyfortress` are removed and the previews of `canyon` are shown
