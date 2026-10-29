# Spec Delta

## Purpose

A ring's preview shows the ring's shape as well as its place: a circle shows its radius, and an oval shows its two axes turned to its
major axis. Previews stay a view of the draft and are never saved.

## MODIFIED Requirements

### Requirement: Each ring has one preview while its map is open
**Priority:** MoSCoW Must

WHILE a map is open, THE SYSTEM SHALL keep exactly one display preview per ring of the draft, placed at the ring center,
oriented along the ring normal, and scaled to the ring's shape: to the radius of a circle, or to the two axes of an oval,
with an oval turned so that its major axis lies along the ring's major axis.

#### Scenario: Map opened with three rings
- **WHEN** a builder opens a map with three rings
- **THEN** three preview entities exist in the setup world, each at its ring's center

#### Scenario: Preview count follows the draft
- **WHEN** a ring is added or removed
- **THEN** the number of preview entities equals the number of rings in the draft before the next tick

#### Scenario: Oval preview
- **WHEN** a builder opens a map whose ring is an oval with semi-major radius 6, semi-minor radius 3 and major axis `(0, 1, 0)` in a ring with normal `(0, 0, 1)`
- **THEN** the ring's preview spans 12 blocks along the major axis and 6 blocks across it, and its long side lies along the major axis
