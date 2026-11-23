# Spec Delta

## Purpose

A guide point bends the racing line between two rings. When a ring moves in the order of the course, the guide point has to keep
bending between the same two rings, or the line flies into terrain the builder had already steered around.

## ADDED Requirements

### Requirement: A guide point stays on the rings it bends between
**Priority:** MoSCoW Must

WHEN a ring is inserted or reordered, THE SYSTEM SHALL remap each guide point so that it still lies between the two rings it
bent between, at the same offset inside that gap. The remapped draft SHALL be saved in the same save as the ring edit.

#### Scenario: Insert shifts a guide point after the insertion
- **WHEN** a ring is inserted at index 2 of a draft with four rings and a guide point between rings 2 and 3
- **THEN** the guide point lies between the former rings 2 and 3, which are now rings 3 and 4, and the draft is saved with both

#### Scenario: Move leaves guide points alone
- **WHEN** ring 2 of a draft with a guide point between rings 2 and 3 is moved by an offset
- **THEN** the guide point is unchanged

#### Scenario: Reordered guide point keeps its gap
- **WHEN** ring 4 of six rings is reordered to position 1, and a guide point lies between rings 3 and 4 at 40 per cent of the gap
- **THEN** the guide point lies between the same two rings, now rings 3 and 4 of the new order, at 40 per cent of that gap
