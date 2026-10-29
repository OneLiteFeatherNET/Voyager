# Spec Delta

## Purpose

A map has one editor at a time, so `/map open` checks the claim of the map before it loads the draft, a builder can give a
map back with `/map close`, and an administrator can release a stuck claim with `/map unlock`. The other `/map` subcommands
keep their behaviour.

## MODIFIED Requirements

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

## ADDED Requirements

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
