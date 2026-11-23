# Spec Delta

## Purpose

An operator can see, on the live node, how long the server's ticks take, how many particle packets it sends, and whether the
statistics writer is losing work. The numbers come from the logs and from the `/race` status view. Reading them never delays a
tick, and a window is counted in ticks, not in wall-clock time.

## ADDED Requirements

### Requirement: The tick summary is logged every 600 ticks
**MoSCoW:** Must. WHEN 600 ticks have run since the last summary, THE SYSTEM SHALL log one INFO line that gives the mean and the
maximum tick duration of those ticks in milliseconds, and SHALL start the next window.

#### Scenario: a full window
- **WHEN** the 600th tick of a window ends
- **THEN** exactly one INFO line gives the mean and the maximum of those 600 durations

#### Scenario: a window is counted in ticks
- **WHEN** the clock is replaced by a fixed value in a test
- **THEN** the summary is still logged on the 600th tick, and no wall-clock wait is needed

### Requirement: Particle packets are counted per window
**MoSCoW:** Should. WHILE a window runs, THE SYSTEM SHALL count the particle packets the server sends, and SHALL include the count
in the window's INFO line.

#### Scenario: a window with fireworks
- **WHEN** a firework burst sends particle packets during a window
- **THEN** the window's INFO line gives a count that includes those packets

### Requirement: The statistics writer's counters are logged with the summary
**MoSCoW:** Should. WHEN the tick summary is logged and statistics are on, THE SYSTEM SHALL include the statistics writer's
`written`, `retried`, `droppedFull` and `droppedData` counts in the same INFO line, and SHALL show the same counts in `/race`.

#### Scenario: statistics are on and a completion is dropped
- **WHEN** the queue is full and a completion is dropped during a window
- **THEN** the window's INFO line shows `droppedFull` above zero

#### Scenario: statistics are off
- **WHEN** `VOYAGER_PERSISTENCE` is `off`
- **THEN** the INFO line shows no statistics counts

### Requirement: The cup status view shows the summary
**MoSCoW:** Should. WHEN a sender runs `/race` with no subcommand, THE SYSTEM SHALL show the most recent completed window's mean
and maximum tick duration, its particle packet count, and, when statistics are on, the writer's counts.

#### Scenario: before the first window completes
- **WHEN** `/race` runs before the first window has completed
- **THEN** the view says that no window has completed yet and shows no zero values as measurements

### Requirement: Observing never delays a tick
**MoSCoW:** Must. WHILE a window runs, THE SYSTEM SHALL keep per tick only a fixed number of counters, SHALL do no I/O on the tick
thread except the one INFO line per window, and SHALL add no dependency to the build.

#### Scenario: a fitness check on the observer
- **WHEN** a class in the observer package imports `java.io`, `java.nio` or `java.net`, or a class outside the window's log class
  calls a logger
- **THEN** the build fails

#### Scenario: the build has no new dependency
- **WHEN** the change is built
- **THEN** the dependency list differs from the one before the change by no artifact
