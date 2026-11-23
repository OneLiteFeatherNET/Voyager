# Spec Delta

## Purpose

A wand click that cannot act on the builder's open map says so once. Before this change, a click made in another world than
the open map's world changed nothing and showed nothing, so the builder clicked again without knowing why.

## ADDED Requirements

### Requirement: A wand click outside the open map's world is answered once
**Priority:** MoSCoW Should

WHEN the builder right-clicks or left-clicks with the wand while the builder stands in a world other than the open map's
world, THE SYSTEM SHALL change nothing and SHALL tell the builder, in one line, that the wand works in the open map `<id>`
and that the builder must stand in it. WHILE the builder repeats such clicks for the same map within 5 seconds of the last
such line, THE SYSTEM SHALL send no further line.

#### Scenario: One click in the wrong world
- **WHEN** a builder with the open map `skyfortress` in world `skyfortress` right-clicks the wand while standing in the lobby world
- **THEN** no ring is placed, the file is not written, and the builder sees one line naming `skyfortress`

#### Scenario: Rapid clicks in the wrong world
- **WHEN** the builder right-clicks the wand ten times within 5 seconds while standing in the lobby world
- **THEN** the builder sees one line in total, and no ring is placed

#### Scenario: Clicks after the window
- **WHEN** the builder right-clicks the wand in the wrong world, then again 6 seconds later on a fake clock
- **THEN** the builder sees a second line

#### Scenario: Click with no open map
- **WHEN** a builder with no open map right-clicks the wand
- **THEN** the builder sees the existing no-map-open message and no other line
