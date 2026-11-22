# setup/map-commands Specification

## Purpose
The builder creates, opens and names maps with `/map` subcommands on the setup server. A map is a draft folder, and
the commands are the only way a draft is created or opened in this change.

## Requirements

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
the wand if the builder does not hold one. IF another builder holds the map, THEN THE SYSTEM SHALL refuse the open and name
the holder, as `setup/map-claims` states.

#### Scenario: Open a draft with three rings
- **WHEN** a builder runs `/map open skyfortress` and the file holds three rings
- **THEN** the builder stands in the map, sees three previews, and the wand is in the builder's inventory

#### Scenario: Open a missing or malformed draft
- **WHEN** a builder runs `/map open <id>` and no draft exists for the id, or the draft file is not valid JSON
- **THEN** the server refuses with a message naming the file, and the builder's current map is unchanged

#### Scenario: Open a map another builder holds
- **WHEN** a builder runs `/map open skyfortress` and another builder holds `skyfortress`
- **THEN** the server refuses with a message naming the holder, and the builder's current map is unchanged

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

### Requirement: Closing a map gives it back
**Priority:** MoSCoW Must

WHEN a builder runs `/map close` in an open map, THE SYSTEM SHALL remove the previews of that map, SHALL release the builder's
claim of it, SHALL leave the builder with no open map, and SHALL reply that the map is closed. WHILE no map is open, THE
SYSTEM SHALL reply that no map is open and change nothing.

#### Scenario: Close the open map
- **WHEN** a builder with `skyfortress` open runs `/map close`
- **THEN** the previews of `skyfortress` are removed, another builder can open `skyfortress`, and `/map spawn` now replies that no map is open

#### Scenario: Close with no map open
- **WHEN** a builder with no open map runs `/map close`
- **THEN** the server replies that no map is open and no file changes

### Requirement: An administrator releases a stuck claim
**Priority:** MoSCoW Must

WHEN a sender who holds `voyager.setup.unlock` runs `/map unlock <id>` for a map that a builder holds, THE SYSTEM SHALL release
that claim, SHALL end the holder's open session on that map, and SHALL reply with the holder's name.

#### Scenario: Unlock a stuck claim
- **WHEN** a sender with `voyager.setup.unlock` runs `/map unlock skyfortress` while builder A holds `skyfortress`
- **THEN** A has no open map, builder B can open `skyfortress`, and the reply names A

### Requirement: Unlock is gated by its own node
**Priority:** MoSCoW Must

IF the sender of `/map unlock <id>` does not hold `voyager.setup.unlock`, THEN THE SYSTEM SHALL tell the sender that the command is
denied and SHALL change nothing. The node `voyager.setup.use` is not checked for this subcommand.

#### Scenario: Unlock without the node
- **WHEN** a builder without `voyager.setup.unlock` runs `/map unlock skyfortress` while builder A holds it
- **THEN** the command is denied, and A still holds `skyfortress` with its session

### Requirement: Unlock of a free map changes nothing
**Priority:** MoSCoW Should

IF no builder holds the map of `/map unlock <id>`, THEN THE SYSTEM SHALL reply that the map is not held and SHALL change nothing.

#### Scenario: Unlock of a map nobody holds
- **WHEN** a sender with `voyager.setup.unlock` runs `/map unlock canyon` and no builder holds `canyon`
- **THEN** the server replies that `canyon` is not held and no file changes
