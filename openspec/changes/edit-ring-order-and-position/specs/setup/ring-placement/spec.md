# Spec Delta

## Purpose

The wand still places a ring only at the end of the course. Ring order is no longer a fixed rule of the wand: the named commands of
`setup/ring-editing` can move, insert and reorder rings, and the wand keeps appending and removing as before.

## MODIFIED Requirements

### Requirement: Rings are never inserted or reordered
**Priority:** MoSCoW Must

THE SYSTEM SHALL insert a ring between existing rings, or reorder rings, only through the named `/map ring insert` and `/map ring
reorder` commands of `setup/ring-editing`. THE SYSTEM SHALL NOT insert or reorder a ring through the wand, through a plain
left-click, or through any other command, and SHALL NOT change the index or the normal of a ring through the wand.

#### Scenario: Wand placement stays at the end
- **WHEN** a builder right-clicks the wand in a map with three rings
- **THEN** the new ring has index 3 and the other rings keep their indices

#### Scenario: A plain left-click reorders nothing
- **WHEN** a builder left-clicks the wand without sneaking
- **THEN** no ring is inserted, reordered or moved, and the draft is unchanged
