# Spec Delta

## Purpose

A builder can move a placed ring, insert a ring between others, and reorder rings with named commands. Each command changes
the draft through the same save path as a placement, so a moved ring is saved before it is shown, and an undo restores it.

## ADDED Requirements

### Requirement: A ring moves to the builder's eye or by an offset
**Priority:** MoSCoW Must

WHEN a builder runs `/map ring move <index> here` or `/map ring move <index> <dx> <dy> <dz>`, THE SYSTEM SHALL move the centre
of ring `<index>` and SHALL keep its normal, SHALL save the draft before the previews change, and SHALL record one undo step.

#### Scenario: Move to the eye
- **WHEN** a builder standing at (12.0, 65.0, -3.0) runs `/map ring move 2 here` on a map with five rings
- **THEN** ring 2 has centre (12.0, 65.6, -3.0), its normal is unchanged, its index is 2, and the draft is saved

#### Scenario: Move by an offset
- **WHEN** a builder runs `/map ring move 2 1 0 -4` on a map with five rings
- **THEN** ring 2's centre moves by (1, 0, -4) and every other ring is unchanged

#### Scenario: Index out of range
- **WHEN** a builder runs `/map ring move 7 here` on a map with five rings
- **THEN** the server refuses with a message naming index 7 and the five rings, and the draft is unchanged

### Requirement: A ring is inserted at an index from the builder's pose
**Priority:** MoSCoW Must

WHEN a builder runs `/map ring insert <index>`, THE SYSTEM SHALL place a ring from the builder's pose, as the wand does, at
position `<index>`, renumber the later rings, remap the guide points, and save the draft before the previews change.

#### Scenario: Insert between two rings
- **WHEN** a builder runs `/map ring insert 1` while looking east on a map with three rings
- **THEN** the draft holds four rings, the new ring has index 1, the former ring 1 and 2 have indices 2 and 3, and each keeps its shape

#### Scenario: Insert past the end
- **WHEN** a builder runs `/map ring insert 9` on a map with three rings
- **THEN** the server refuses with a message naming the range 0 to 3, and the draft is unchanged

### Requirement: A ring is reordered by from and to
**Priority:** MoSCoW Must

WHEN a builder runs `/map ring reorder <from> <to>`, THE SYSTEM SHALL move the ring at `<from>` to position `<to>`, renumber the
rings, remap the guide points, and save the draft before the previews change.

#### Scenario: Move ring 4 to position 1
- **WHEN** a builder runs `/map ring reorder 4 1` on a map with six rings
- **THEN** the former ring 4 is ring 1, the former rings 1 to 3 are rings 2 to 4, rings 0 and 5 are unchanged, and the draft is saved

#### Scenario: Reorder that breaks a guide point
- **WHEN** a builder runs `/map ring reorder` in a way that leaves a guide point between two rings it no longer follows, and the bracketing rule fails
- **THEN** the server refuses with a message naming the guide point, and the draft is unchanged

### Requirement: Each ring command is one undo step
**Priority:** MoSCoW Must

WHEN a ring is moved, inserted or reordered, THE SYSTEM SHALL record the draft before the command as one step of the undo history, so that `/map undo` restores the rings and the guide points together.

#### Scenario: Undo of a reorder
- **WHEN** a builder runs `/map ring reorder 4 1` and then `/map undo`
- **THEN** the draft holds the rings in their former order with their former indices, and its guide points are the ones it had before the reorder
