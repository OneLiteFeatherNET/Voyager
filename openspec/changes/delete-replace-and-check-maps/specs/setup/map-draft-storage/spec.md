# Spec Delta

## Purpose

A draft or a world that a builder removes is kept in a trash folder, not deleted, so that a mistaken delete or replace can be recovered by
hand. Nothing in the setup data directory is removed outright by a builder command.

## ADDED Requirements

### Requirement: Removed drafts and worlds go to the trash, not the bin
**Priority:** MoSCoW Must

WHEN a draft or a world is removed by a builder command, THE SYSTEM SHALL move it to `drafts/trash/<id>-<timestamp>/`, where the timestamp is
read from the injected clock, SHALL keep its bytes unchanged, and SHALL NOT overwrite an existing trash folder. IF a move fails, THEN THE SYSTEM
SHALL leave the draft and the world at their paths and SHALL report the failure.

#### Scenario: Trash folder already exists
- **WHEN** a move targets a trash folder that already exists
- **THEN** the move is refused, and nothing is overwritten or moved

#### Scenario: Trashed files keep their bytes
- **WHEN** `drafts/canyon.json` is moved to the trash
- **THEN** the file under `drafts/trash/canyon-<timestamp>/` has the same bytes as the file before the move

### Requirement: The trash is not read as a draft
**Priority:** MoSCoW Must

THE SYSTEM SHALL NOT read, list or open a draft from `drafts/trash/`, and a draft id SHALL remain free for `/map new` while its trash folder exists.

#### Scenario: New map with a trashed id
- **WHEN** a builder runs `/map new canyon` and `drafts/trash/canyon-<timestamp>/` exists but `drafts/canyon.json` and `<worldsPath>/canyon` do not
- **THEN** the map is created and the trash folder is unchanged
