# setup/guide-authoring Specification

## Purpose

The builder authors the racing line of an open map with `/map guide` commands. A guide point is placed where the builder stands
and is bound to the two rings it bends between by the game's own order rule, so the builder never types an index.

## ADDED Requirements

### Requirement: A guide point is added at the builder's eye
**Priority:** MoSCoW Must

WHEN a builder runs `/map guide add` in an open map with at least two rings, THE SYSTEM SHALL add a guide point at the builder's
eye position, SHALL derive its order index from the two rings the point lies between, SHALL save the draft before the markers
change, and SHALL record one undo step. IF the map has fewer than two rings, THEN THE SYSTEM SHALL refuse and change nothing.

#### Scenario: Point between rings 2 and 3
- **WHEN** a builder whose eye is nearest the segment from ring 2 to ring 3 of a map with five rings runs `/map guide add`
- **THEN** the draft holds one more guide point, its order index is between 200 and 300 exclusive, and the draft is saved

#### Scenario: Map with one ring
- **WHEN** a builder runs `/map guide add` on a map with one ring
- **THEN** the server refuses with a message that a guide point needs two rings, and the draft is unchanged

### Requirement: A guide point that the game would refuse is refused before save
**Priority:** MoSCoW Must

IF a guide change would produce a guide line that `MapDefinition` refuses, THEN THE SYSTEM SHALL refuse the change with the bracket
message named in the builder's language, SHALL NOT write the file, and SHALL change nothing.

#### Scenario: Candidate left past the last ring
- **WHEN** a guide change produces a candidate draft in which a guide point lies after the last ring, so that `MapDefinition` refuses it
- **THEN** the server refuses with the bracket message, and the draft is unchanged

Note: `add` itself never derives a point past the last ring, because the derivation stops at the second-to-last ring; the scenario
guards every other guide change against a candidate the game would refuse.

### Requirement: Guide points are listed and removed by number
**Priority:** MoSCoW Must

WHEN a builder runs `/map guide list` in an open map, THE SYSTEM SHALL reply with each guide point numbered from 1 in the order of its
order index, and with the two rings it bends between. WHEN a builder runs `/map guide remove <n>`, THE SYSTEM SHALL remove guide point
`<n>`, SHALL save the draft, and SHALL record one undo step. IF `<n>` is not a number of a guide point, THEN THE SYSTEM SHALL refuse with
the count of guide points and change nothing.

#### Scenario: List two points
- **WHEN** a builder runs `/map guide list` on a map with two guide points
- **THEN** the reply lists two numbered lines, each naming the two rings it bends between

#### Scenario: Remove by number
- **WHEN** a builder runs `/map guide remove 2` on a map with two guide points
- **THEN** one guide point is left, and the draft is saved

### Requirement: Spacing and look-ahead are set within the game's bounds
**Priority:** MoSCoW Should

WHEN a builder runs `/map guide spacing <blocks>` with a value from 0.25 up, or `/map guide lookahead <n>` with a value of at least 1,
THE SYSTEM SHALL set the value, SHALL save the draft, and SHALL show a note that look-ahead applies in WINDOW mode only when that mode is
not set. IF a value is below its bound, THEN THE SYSTEM SHALL refuse with the bound and change nothing.

#### Scenario: Spacing below the minimum
- **WHEN** a builder runs `/map guide spacing 0.1`
- **THEN** the server refuses with the minimum 0.25, and the draft is unchanged

#### Scenario: Look-ahead in full mode
- **WHEN** a builder runs `/map guide lookahead 3` on a map whose mode is FULL
- **THEN** the look-ahead is saved as 3 and the reply notes that it applies in WINDOW mode only

### Requirement: Guide points are shown as markers while the map is open
**Priority:** MoSCoW Should

WHILE a map is open, THE SYSTEM SHALL show one marker for each guide point in the setup world, and SHALL remove the markers when the
map is closed, switched or the builder disconnects. Markers SHALL NOT be saved in the draft.

#### Scenario: Marker after add
- **WHEN** a builder adds a guide point in an open map
- **THEN** one marker appears at the point, and the draft file holds no marker data

#### Scenario: Markers removed on close
- **WHEN** a builder closes the map with three guide points
- **THEN** no marker of that map remains in the setup world
