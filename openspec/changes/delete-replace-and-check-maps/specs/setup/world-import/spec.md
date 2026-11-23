# Spec Delta

## Purpose

An import can replace an incomplete draft, so a builder who imported the wrong world can import the right one without deleting files by
hand. The default import still refuses an id that is in use.

## MODIFIED Requirements

### Requirement: An import copies a world's region data into a new map
**Priority:** MoSCoW Must

WHEN a builder runs `/map import <world-folder> <id>` with a valid world folder name and a valid id, and neither `drafts/<id>.json`,
`maps/<id>.json` nor `<worldsPath>/<id>` exists, THE SYSTEM SHALL copy the overworld region files of that world into
`<worldsPath>/<id>/region/`, SHALL create `drafts/<id>.json` with world `<id>`, and SHALL reply with the report and the command
`/map open <id>`. IF a draft named `<id>` exists and the builder did not give `--replace`, THEN THE SYSTEM SHALL refuse the import with a
message naming the id and the `--replace` option, and no file or folder SHALL change.

#### Scenario: Import of a world with region files at its root
- **WHEN** a builder runs `/map import elytrarace-cube skycube` and the folder `elytrarace-cube` under the import root holds `region/` with two region files
- **THEN** `<worldsPath>/skycube/region/` holds the two region files, `drafts/skycube.json` exists with world `skycube`, and the reply names `/map open skycube`

#### Scenario: Id already in use
- **WHEN** a builder runs `/map import elytrarace-cube skycube` and a draft named `skycube` exists
- **THEN** the command is refused with a message naming the id and the `--replace` option, and no file or folder changes

#### Scenario: Replace requested
- **WHEN** a builder runs `/map import elytrarace-cube skycube --replace` and a draft named `skycube` exists with no spawn
- **THEN** the import replaces the draft as `setup/map-commands` states for `--replace`, and the previous draft is in the trash
