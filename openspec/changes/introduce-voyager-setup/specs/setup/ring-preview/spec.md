# Spec Delta

## Purpose

While a map is open, the builder sees each ring as a display preview in the setup world. Previews are a view of the draft
and never part of the saved data.

## ADDED Requirements

### Requirement: Each ring has one preview while its map is open
**Priority:** MoSCoW Must

WHILE a map is open, THE SYSTEM SHALL keep exactly one display preview per ring of the draft, placed at the ring center,
oriented along the ring normal, and scaled to the ring radius.

#### Scenario: Map opened with three rings
- **WHEN** a builder opens a map with three rings
- **THEN** three preview entities exist in the setup world, each at its ring's center

#### Scenario: Preview count follows the draft
- **WHEN** a ring is added or removed
- **THEN** the number of preview entities equals the number of rings in the draft before the next tick

### Requirement: Previews are not saved
**Priority:** MoSCoW Must

THE SYSTEM SHALL NOT write preview entities into `map.json` or into the world folder.

#### Scenario: Save after placing a ring
- **WHEN** a ring is placed and the draft is saved
- **THEN** `map.json` holds the ring data and nothing else about the preview
