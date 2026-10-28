## MODIFIED Requirements

### Requirement: A racer is shown the racing line from the next ring to the last by default
**MoSCoW:** Must. WHILE a racer is on a map whose guide line mode is `FULL`, THE SYSTEM SHALL draw the racing line of that map
from the last ring the racer passed to the last ring of the map, so that the segment the racer is flying stays drawn until
the next ring is passed. Before the first ring is passed, the line starts at the first ring. The part of the line behind the
last ring passed SHALL NOT be drawn.

The header keeps its wording so that the archived requirement matches. Its "next ring" is the ring the racer is heading for;
the line now starts one ring earlier, at the last ring passed.

#### Scenario: Whole course at the start
- **WHEN** a racer is at the start of a map that has no `mode` set, and every point of the line is within the reach
- **THEN** the racer is sent the particles of the whole line, from the first ring's centre to the last

#### Scenario: Flown stretch is not drawn
- **WHEN** a racer has passed the first ten rings of a `FULL` map and is heading for the eleventh
- **THEN** the racer is sent the particles from the tenth ring's centre to the last ring's centre, and none behind the tenth
  ring

#### Scenario: The line stays until the next ring is passed
- **WHEN** a racer has passed the tenth ring and is still between the tenth and the eleventh
- **THEN** the racer is still sent the particles from the tenth ring's centre onwards

#### Scenario: Last ring
- **WHEN** a racer is heading for the last ring of a `FULL` map
- **THEN** the racer is sent the particles from the last ring passed to the last ring, and nothing after the last ring

### Requirement: A window mode shows only the stretch ahead
**MoSCoW:** Must. WHILE a map's guide line mode is `WINDOW`, THE SYSTEM SHALL draw the stretch of the line from the last ring
the racer passed to `lookAheadRings` rings past the ring they are heading for, and nothing once every ring is passed.

#### Scenario: Window of two rings
- **WHEN** a racer is heading for ring 4 of a `WINDOW` map with `lookAheadRings` 2
- **THEN** the racer is sent the particles from ring 3's centre to ring 6's centre, and no others

#### Scenario: Last ring
- **WHEN** a racer is heading for the last ring of a `WINDOW` map
- **THEN** the racer is sent the particles from the last ring passed to the last ring, and nothing after it
