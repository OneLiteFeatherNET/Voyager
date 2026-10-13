# Delta for race/racing-line-display

## Purpose

What a racer is shown of the racing line of the map being raced: the course ahead by default, the look-ahead window as
a per-map option, how the line is drawn, how often it is redrawn, and how far from the racer it is sent. The shape of
the line, the rings and the scoring are owned by the course and are not described here.

## ADDED Requirements

### Requirement: A racer is shown the racing line from the next ring to the last by default
**MoSCoW:** Must. WHILE a racer is on a map whose guide line mode is `FULL`, THE SYSTEM SHALL draw the racing line of
that map from the ring the racer is heading for to the last ring. The part of the line the racer has already flown SHALL
NOT be drawn.

#### Scenario: Whole course at the start
- **WHEN** a racer is at the first ring of a map that has no `mode` set, and every point of the line is within the
  view distance
- **THEN** the racer is sent the particles of the whole line, from the first ring's centre to the last

#### Scenario: Flown stretch is not drawn
- **WHEN** a racer has passed the first ten rings of a `FULL` map and is heading for the eleventh
- **THEN** the racer is sent the particles from the eleventh ring's centre to the last ring's centre, and none behind the
  eleventh ring

#### Scenario: Last ring
- **WHEN** a racer is heading for the last ring of a `FULL` map
- **THEN** the racer is sent no particles, because nothing lies ahead of the last ring to draw

### Requirement: A window mode shows only the stretch ahead
**MoSCoW:** Must. WHILE a map's guide line mode is `WINDOW`, THE SYSTEM SHALL draw the stretch of the line from the ring
the racer is heading for to `lookAheadRings` rings past it, and nothing once every ring is passed. This is the policy
the renderer has today.

#### Scenario: Window of two rings
- **WHEN** a racer is heading for ring 4 of a `WINDOW` map with `lookAheadRings` 2
- **THEN** the racer is sent the particles from ring 4's centre to ring 6's centre, and no others

#### Scenario: Last ring
- **WHEN** a racer is heading for the last ring of a `WINDOW` map
- **THEN** the racer is sent no particles, because nothing lies ahead of the last ring to draw

### Requirement: Far points are not sent
**MoSCoW:** Must. THE SYSTEM SHALL NOT send a particle of the racing line to a racer when the particle is more than 160
blocks from that racer. The cut-off applies to both modes.

#### Scenario: Distant part of the course
- **WHEN** a racer is 300 blocks from the far end of a `FULL` course
- **THEN** no particle of the far end is sent to that racer on that redraw

#### Scenario: Near part of the course
- **WHEN** a racer is 40 blocks from a point of the line
- **THEN** that point is sent to that racer on the redraw

### Requirement: The line is redrawn often enough to show no gap
**MoSCoW:** Must. THE SYSTEM SHALL redraw the racing line of a racer at an interval no longer than half the shortest
life of the particle the map uses, so that the line shows no gap between two redraws.

#### Scenario: Dust redrawn inside its shortest life
- **WHEN** a map uses `DUST`
- **THEN** the line is redrawn at least every 4 ticks

#### Scenario: End rod redrawn inside its shortest life
- **WHEN** a map uses `END_ROD`
- **THEN** the line is redrawn at least every 6 ticks. The owner proposed 10, which needs END_ROD's shortest life to be at
  least 20 ticks; that life could not be read, so 6 is used (design D4)

### Requirement: The line is drawn with the map's particle and colour
**MoSCoW:** Must. THE SYSTEM SHALL draw the racing line with the particle the map names, `END_ROD` when none is named,
and, for `DUST`, in the colour the map names, `#FFC800` when none is named.

#### Scenario: Default look
- **WHEN** a map names no particle and no colour
- **THEN** the line is drawn as END_ROD

#### Scenario: Dust in the map's colour
- **WHEN** a map names `DUST` and `#3366FF`
- **THEN** the line is drawn as blue dust particles of scale 1.0

### Requirement: Display choices do not change the race
**MoSCoW:** Must. THE SYSTEM SHALL NOT change any ring crossing, score, boost, physics or run result because of the
mode, particle, colour, spacing or cut-off. The racing line is presentation for the racer.

#### Scenario: Same result with and without the line
- **WHEN** the same racer flies the same course with `FULL` and with `WINDOW`
- **THEN** the run result, the scored rings and the finish time are the same

### Requirement: Budget is stated for the shipped course
**MoSCoW:** Must. THE SYSTEM SHALL keep, for the shipped map with sixteen racers, at most 30,000 particle packets per
second in total and at most 2,000 per racer, as set out in the design (D6). The owner set these limits on 2026-10-13.

#### Scenario: Shipped course at full race size
- **WHEN** sixteen racers are spread along the shipped 35-ring map, each in FULL mode, with the 160-block reach applied
- **THEN** the total particle packets per second do not exceed 30,000, and no racer is sent more than 2,000 per second

#### Scenario: The reach carries the per-racer limit
- **WHEN** a racer stands at the first ring of the shipped map in FULL mode, and the reach is not applied
- **THEN** that racer would be sent more than 2,000 packets per second at a 6-tick END_ROD refresh, which is why the
  reach is part of the budget and is not removed (design D6, measured)
