# Spec Delta

## Purpose

A builder who makes a mistake in an open map can step back through the last edits with `/map undo`, and step forward again with
`/map redo`. The history is kept in memory for the open map only, and the file on disk is always the last saved state.

## ADDED Requirements

### Requirement: Undo restores the draft before the last edit
**Priority:** MoSCoW Must

WHEN a builder runs `/map undo` in an open map and an earlier draft exists, THE SYSTEM SHALL save that earlier draft, SHALL make it
the current draft only after the save has succeeded, SHALL show its previews, and SHALL reply with the ring count of the restored
draft.

#### Scenario: Undo a placed ring
- **WHEN** a builder places a ring in a map with two rings and runs `/map undo`
- **THEN** the draft holds two rings, the file holds two rings, the previews show two rings, and the reply gives the count 2

#### Scenario: Undo a removed ring
- **WHEN** a builder removes ring 1 of three rings and runs `/map undo`
- **THEN** the draft holds three rings and ring 1 is the ring that was removed, at index 1

### Requirement: Redo re-applies the edit that undo took back
**Priority:** MoSCoW Must

WHEN a builder runs `/map redo` in an open map after an undo, THE SYSTEM SHALL save the undone draft, SHALL make it the current draft
only after the save has succeeded, and SHALL show its previews.

#### Scenario: Redo after undo
- **WHEN** a builder places a ring, runs `/map undo`, and then runs `/map redo`
- **THEN** the draft holds the placed ring again and the file holds it too

### Requirement: A new edit clears the redo history
**Priority:** MoSCoW Must

WHEN a builder makes an edit after an undo, THE SYSTEM SHALL discard the undone drafts, so that `/map redo` then has nothing to re-apply.

#### Scenario: Edit after undo
- **WHEN** a builder places a ring, runs `/map undo`, places another ring, and runs `/map redo`
- **THEN** `/map redo` replies that nothing can be redone and the draft holds the second ring only

### Requirement: Every edit is recorded, and opening a map is not an edit
**Priority:** MoSCoW Must

THE SYSTEM SHALL record as an edit each placed ring, each removed ring, each set spawn, each ring field edit of
`setup/ring-editing`, and each map field edit (name, authors and reference time) of `map-name-and-authors`, all through the same
save path. THE SYSTEM SHALL NOT record the opening of a map, or an undo or a redo, as an edit.

#### Scenario: Opening a map is not an edit
- **WHEN** a builder opens a map and runs `/map undo`
- **THEN** the server replies that nothing can be undone and the file does not change

#### Scenario: Undo is not recorded
- **WHEN** a builder places a ring, runs `/map undo`, and runs `/map redo`
- **THEN** a second `/map undo` restores the draft from before the placed ring, and not an intermediate state

### Requirement: Cup changes are not undone
**Priority:** MoSCoW Must

THE SYSTEM SHALL keep cup changes out of the undo history, and SHALL NOT restore a cup on `/map undo` or `/map redo`.

#### Scenario: Cup change is kept
- **WHEN** a builder changes a cup and then runs `/map undo` in an open map
- **THEN** the cup keeps its change, and the map's draft is restored to its state before the last map edit

### Requirement: The history holds at most 100 earlier drafts
**Priority:** MoSCoW Should

THE SYSTEM SHALL keep at most 100 earlier drafts for an open map, and WHEN an edit would make it hold 101, THE SYSTEM SHALL drop
the oldest.

#### Scenario: The oldest draft is dropped
- **WHEN** a builder makes 101 edits to one map and then undoes 100 times
- **THEN** the draft is the one after the first edit, and no further undo is possible

### Requirement: The history belongs to one open map
**Priority:** MoSCoW Must

THE SYSTEM SHALL keep the undo history in memory only, SHALL drop it when the builder opens a different map, closes the map,
disconnects, or the setup server restarts, and SHALL NOT write it to disk.

#### Scenario: Opening another map drops the history
- **WHEN** a builder places a ring in `skyfortress`, opens `canyon`, and later opens `skyfortress` again
- **THEN** `/map undo` in `skyfortress` replies that nothing can be undone

### Requirement: A failed save of an undo or redo keeps the current draft
**Priority:** MoSCoW Must

IF the save of an undo or redo fails, THEN THE SYSTEM SHALL keep the current draft and the undo history unchanged, and SHALL tell the
builder that the change was not saved.

#### Scenario: Disk is full during undo
- **WHEN** a builder runs `/map undo` and the save fails
- **THEN** the draft and the history are as they were before the command, the previews are unchanged, and the builder sees the save-failed message

### Requirement: Undo and redo need the setup permission
**Priority:** MoSCoW Must

THE SYSTEM SHALL run `/map undo` and `/map redo` only for a builder who holds `voyager.setup.use`, and SHALL tell any other sender
that the command is denied, as every other `/map` subcommand does.

#### Scenario: Builder without the node
- **WHEN** a builder without `voyager.setup.use` runs `/map undo`
- **THEN** the builder is told the command is denied, and no file changes
