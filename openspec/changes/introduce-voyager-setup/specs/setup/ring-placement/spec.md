# Spec Delta

## Purpose

The builder places and removes disc rings with the wand, from the builder's own pose. This is the authoring model of
ADR-0018: one click per ring, and the ring stays the disc of `net.elytrarace.voyager.api.race.Ring`.

## ADDED Requirements

### Requirement: Right-click places a ring from the builder's pose
**Priority:** MoSCoW Must

WHEN the builder right-clicks with the wand in an open map, THE SYSTEM SHALL append one ring whose center is the builder's
eye position, whose normal is the unit length look direction, whose radius is the default radius, whose points are the
default points, whose type is `STANDARD`, and whose index is the number of rings before it, and SHALL autosave the draft.

#### Scenario: Ring placed at eye height looking east
- **WHEN** a builder with eye position (10.0, 65.6, 4.0) and look direction (1, 0, 0) right-clicks the wand in a map with two rings
- **THEN** the map holds a third ring with index 2, center (10.0, 65.6, 4.0), normal (1, 0, 0), radius 3.605551275463989, points 10 and type `STANDARD`

#### Scenario: Look direction is not normalised by the client
- **WHEN** the look direction reported to the server has length 2
- **THEN** the normal stored in the ring has length 1 and the direction is preserved

### Requirement: A ring from a degenerate pose is refused
**Priority:** MoSCoW Must

IF the look direction has zero length or a non-finite component, THEN THE SYSTEM SHALL leave the draft unchanged and SHALL
tell the builder that no ring was placed.

#### Scenario: Zero look vector
- **WHEN** the look direction is (0, 0, 0)
- **THEN** no ring is added, the file is not written, and the builder sees a message that no ring was placed

### Requirement: Sneak and left-click remove the ring the look ray crosses
**Priority:** MoSCoW Must

WHEN the builder sneaks and left-clicks with the wand in an open map, THE SYSTEM SHALL remove the nearest ring whose disc the
look ray crosses within 32 blocks of the eye, SHALL renumber later rings so that index equals list position, and SHALL autosave
the draft. IF no ring is crossed, THEN THE SYSTEM SHALL change nothing and SHALL tell the builder.

#### Scenario: Remove the middle ring of three
- **WHEN** a builder sneaks, looks through ring 1 of three rings and left-clicks
- **THEN** the draft holds two rings, the former ring 2 now has index 1, and the file is saved

#### Scenario: Nothing in the line of sight
- **WHEN** a builder sneaks and left-clicks while the look ray crosses no ring within 32 blocks
- **THEN** the draft is unchanged and the builder sees a message that no ring was found

### Requirement: A plain left-click changes no ring
**Priority:** MoSCoW Must

WHEN the builder left-clicks with the wand without sneaking, THE SYSTEM SHALL change no ring, SHALL NOT write the file, and
SHALL send no removal or not-found message (owner decision O2, 2026-10-10: sneak plus left-click is the removal gesture until
undo exists).

#### Scenario: Plain left-click through a ring
- **WHEN** a builder who is not sneaking looks through ring 1 of three rings and left-clicks
- **THEN** the draft is unchanged, the file is not written, and the builder sees no removal or not-found message

### Requirement: Rings are only appended and removed in this change
**Priority:** MoSCoW Must

THE SYSTEM SHALL NOT insert a ring between existing rings, reorder rings, or edit a ring's fields in this change.

#### Scenario: Insertion requested
- **WHEN** any command or click would insert a ring at an index below the last
- **THEN** the system has no such operation and the draft is unchanged

### Requirement: Every change is saved before the builder sees it
**Priority:** MoSCoW Must

WHEN a placement, removal or spawn change is made, THE SYSTEM SHALL save the draft first and SHALL show the change only if
the save succeeded. IF the save fails, THEN THE SYSTEM SHALL keep the previously saved draft in memory and in the file,
and SHALL tell the builder that the change was not saved.

#### Scenario: Disk is full
- **WHEN** a placement is made and the write fails
- **THEN** the draft in memory equals the draft on disk before the click, no preview is spawned, and the builder sees a save-failed message

### Requirement: Block breaking and placing are cancelled in the setup world
**Priority:** MoSCoW Should

WHILE a map is open, THE SYSTEM SHALL cancel block placement and block breaking by players, so that terrain edits the
server cannot persist are not made silently.

#### Scenario: Builder breaks a block
- **WHEN** a builder left-clicks a block while holding any item in an open map
- **THEN** the block stays and the block-break event is cancelled; a sneak plus left-click with the wand is still handled as a removal, see the removal requirement
