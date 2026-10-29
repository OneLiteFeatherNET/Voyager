# Spec Delta

## Purpose

A map has one editor at a time, so `/map open` checks the claim of the map before it loads the draft, and a builder can
give a map back with `/map close`. The other `/map` subcommands keep their behaviour.

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
