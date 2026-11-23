## MODIFIED Requirements

### Requirement: The line is redrawn often enough to show no gap
**MoSCoW:** Must. THE SYSTEM SHALL redraw the racing line of a racer at an interval no longer than half the shortest
life of the particle the map uses, so that the line shows no gap between two redraws. For points more than 60 blocks from the
racer, THE SYSTEM SHALL redraw at half that rate, on alternate refreshes, so that the far line keeps its shape but may show a
gap between redraws that is at most one refresh longer.

#### Scenario: Dust redrawn inside its shortest life
- **WHEN** a map uses `DUST`
- **THEN** the line is redrawn at least every 4 ticks

#### Scenario: End rod redrawn inside its shortest life
- **WHEN** a map uses `END_ROD`
- **THEN** the line is redrawn at least every 6 ticks. The owner proposed 10, which needs END_ROD's shortest life to be at
  least 20 ticks; that life could not be read, so 6 is used (design D4)

#### Scenario: Far points redrawn at half rate
- **WHEN** a map uses `END_ROD` and a point of the line is 100 blocks from the racer
- **THEN** that point is sent on alternate refreshes, and a point 40 blocks away is sent on every refresh

## ADDED Requirements

### Requirement: Each racer may hide their own racing line
**MoSCoW:** Should. THE SYSTEM SHALL let a racer hide their own racing line with a toggle, and the line is shown by default. WHILE the
line is hidden, THE SYSTEM SHALL send that racer no racing-line particles, and SHALL NOT change any other racer's line.

#### Scenario: Hidden line
- **WHEN** a racer turns their line off
- **THEN** no racing-line particle is sent to that racer until they turn it on again, and other racers still receive their lines

#### Scenario: Default on
- **WHEN** a racer joins a session and has not changed the toggle
- **THEN** their line is shown

### Requirement: Built packets are reused across racers
**MoSCoW:** Should. THE SYSTEM SHALL build the racing-line packets of a map once, and SHALL select for each racer the packets within
reach of that racer, without rebuilding them per racer per refresh.

#### Scenario: Two racers on the same map
- **WHEN** two racers are refreshed on the same tick
- **THEN** both are sent packets from the same prebuilt set, filtered by their own reach
