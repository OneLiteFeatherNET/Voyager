# Spec Delta

## Purpose

The removal gesture of the setup server finds the ring that the look ray crosses, and that ring is a circle or an oval. A
removal renumbers the rings after it, and it must keep the shape of each ring that stays, so that removing one ring never
changes another ring's course.

## MODIFIED Requirements

### Requirement: Sneak and left-click remove the ring the look ray crosses
**Priority:** MoSCoW Must

WHEN the builder sneaks and left-clicks with the wand in an open map, THE SYSTEM SHALL remove the nearest ring whose shape the
look ray crosses within 32 blocks of the eye, SHALL renumber later rings so that index equals list position, SHALL keep the
shape of every ring that stays, and SHALL autosave the draft. A ring's shape is the disc of a circle or the area of an oval, as
`race/ring-shape` defines it. IF no ring is crossed, THEN THE SYSTEM SHALL change nothing and SHALL tell the builder.

#### Scenario: Remove the middle ring of three
- **WHEN** a builder sneaks, looks through ring 1 of three rings and left-clicks
- **THEN** the draft holds two rings, the former ring 2 now has index 1, and the file is saved

#### Scenario: Nothing in the line of sight
- **WHEN** a builder sneaks and left-clicks while the look ray crosses no ring within 32 blocks
- **THEN** the draft is unchanged and the builder sees a message that no ring was found

#### Scenario: Oval after the removed ring keeps its shape
- **WHEN** a builder sneaks, looks through ring 0 of two rings, where ring 1 is an oval, and left-clicks
- **THEN** the draft holds one ring, which is the former ring 1 with index 0, and it is still an oval with the same semi-major radius, semi-minor radius and major axis

#### Scenario: Circumscribed circle outside an oval is not a hit
- **WHEN** a builder sneaks and left-clicks while the look ray crosses an oval's circumscribed circle outside the oval, and no other ring is crossed
- **THEN** the draft is unchanged and the builder sees a message that no ring was found
