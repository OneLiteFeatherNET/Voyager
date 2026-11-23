# Spec Delta

## Purpose

A builder can delete a draft that was never published, so its id can be used again, and nothing is lost by the delete.

## ADDED Requirements

### Requirement: A draft is deleted by moving it to the trash
**Priority:** MoSCoW Must

WHEN a builder runs `/map delete <id> confirm` for an unheld draft in `drafts/`, THE SYSTEM SHALL move the draft and its world into
`drafts/trash/<id>-<timestamp>/` and SHALL reply with that folder. Without `confirm`, THE SYSTEM SHALL only reply with what would move.

#### Scenario: Delete a draft
- **WHEN** a builder runs `/map delete canyon confirm` for a draft `canyon` in `drafts/` with no cup naming it
- **THEN** `drafts/canyon.json` and `<worldsPath>/canyon` no longer exist at their paths, both exist under `drafts/trash/canyon-<timestamp>/`, and `/map new canyon` now succeeds

#### Scenario: Delete without confirm
- **WHEN** a builder runs `/map delete canyon`
- **THEN** the reply names the two items that would move, and no file or folder changes

#### Scenario: Refusal for a held map
- **WHEN** a builder runs `/map delete canyon confirm` while another builder holds `canyon`
- **THEN** the command is refused with a message naming the holder, and nothing changes

#### Scenario: Delete a published map
- **WHEN** a builder runs `/map delete canyon confirm` and `maps/canyon.json` exists
- **THEN** the command is refused with a message that a map in `maps/` cannot be deleted here, and nothing changes

#### Scenario: Delete a map a cup names
- **WHEN** a builder runs `/map delete canyon confirm` and a cup file names `canyon`
- **THEN** the command is refused with a message naming the cup, and nothing changes

### Requirement: A draft is replaced by an import when it has no published revision
**Priority:** MoSCoW Should

WHEN a builder runs `/map import <folder> <id> --replace` and the draft `<id>` is in `drafts/` and is not held by a builder, THE SYSTEM
SHALL copy the world into a new folder, SHALL move the previous world and draft into the trash, and SHALL move the new world and draft into
place. IF the copy fails, THEN THE SYSTEM SHALL keep the previous draft and world and SHALL name the failure.

#### Scenario: Replace an incomplete draft
- **WHEN** a builder runs `/map import elytrarace-cube skycube --replace` and `drafts/skycube.json` exists with no spawn
- **THEN** `drafts/skycube.json` is the imported draft, the previous one is in `drafts/trash/skycube-<timestamp>/`, and the reply names `/map open skycube`

#### Scenario: Copy fails during replace
- **WHEN** the copy of a `--replace` import fails for a region file
- **THEN** `drafts/skycube.json` and `<worldsPath>/skycube` are unchanged, and the reply names the failure

#### Scenario: Replace a game-loadable draft
- **WHEN** a builder runs `/map import elytrarace-cube skycube --replace` and `maps/skycube.json` exists
- **THEN** the command is refused, and nothing changes
