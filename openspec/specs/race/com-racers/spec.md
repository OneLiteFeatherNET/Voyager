# race/com-racers Specification

## Purpose
TBD - created by archiving change com-racers. Update Purpose after archive.

## Requirements

### Requirement: Computer racers fill the grid at cup start
**MoSCoW:** Must. WHEN a cup starts with at least `VOYAGER_MIN_PLAYERS` human racers, THE SYSTEM SHALL add computer racers
to the start grid until the grid holds `VOYAGER_COM_COUNT` racers in total, humans included.

#### Scenario: Grid filled to the configured count
- **WHEN** a cup starts with two human racers and `VOYAGER_COM_COUNT` is 5
- **THEN** the grid holds five racers, of which three are computer racers

#### Scenario: No computer racers when the count is zero
- **WHEN** a cup starts with two human racers and `VOYAGER_COM_COUNT` is 0
- **THEN** the grid holds two racers and no computer racer is created

#### Scenario: Humans already fill the grid
- **WHEN** a cup starts with 16 human racers and `VOYAGER_COM_COUNT` is 5
- **THEN** no computer racer is added

### Requirement: Computer racers are named COM n in grid order
**MoSCoW:** Must. THE SYSTEM SHALL name the computer racers `COM 1` to `COM n` in the order of their grid seats, and keep a
name for the life of the cup.

#### Scenario: Names follow the seats
- **WHEN** three computer racers are added to a grid
- **THEN** they are named `COM 1`, `COM 2` and `COM 3` in seat order

### Requirement: Computer racers never count as online players
**MoSCoW:** Must. THE SYSTEM SHALL keep computer racers out of the set of online players that the start gate, the join line,
the boss bar, the waiting room and `VOYAGER_MIN_PLAYERS` read.

#### Scenario: A cup does not start on computer racers alone
- **WHEN** no human racer is online and `VOYAGER_COM_COUNT` is 15
- **THEN** no cup starts and no computer racer is created

#### Scenario: The join line counts humans only
- **WHEN** one human racer joins a server with `VOYAGER_COM_COUNT` set to 5 and a cup running
- **THEN** the join line reads `(1/16)` and not a count that includes the computer racers

### Requirement: Computer racers follow the racing line by arc length
**MoSCoW:** Must. WHILE a cup is running, THE SYSTEM SHALL move each computer racer along the racing line of the current map
by arc length, at a target speed, and set its position from the line, an offset and the racer's seeded state, with no
physics simulation.

#### Scenario: A computer racer advances along the line
- **WHEN** a computer racer has run for one tick at a steady speed on a straight stretch
- **THEN** its position is one speed-step further along the racing line from its previous position

#### Scenario: No elytra physics is run for a computer racer
- **WHEN** a computer racer moves
- **THEN** no flight sample, no velocity exit and no flight tracker entry is created for it

### Requirement: Computer racer movement is deterministic for a seed
**MoSCoW:** Must. THE SYSTEM SHALL compute every computer racer's movement as a pure function of the round seed, the seat,
the difficulty and the tick, so that the same seed and difficulty give the same positions on every run.

#### Scenario: Same seed, same race
- **WHEN** two cups run with the same round seed, difficulty and course
- **THEN** every computer racer is at the same position on every tick in both runs

#### Scenario: Different seeds differ
- **WHEN** two computer racers on the same course have different seats
- **THEN** their offsets or personal speeds differ, so they do not drive the same line

### Requirement: Computer racers never exceed the reference speed
**MoSCoW:** Must. THE SYSTEM SHALL cap a computer racer's speed at the reference speed of the physics reference, and keep
its speed at or above the floor, whatever the difficulty.

#### Scenario: HARD does not exceed the cap
- **WHEN** a HARD computer racer's target speed is computed on a straight stretch
- **THEN** the target speed is at most the reference cap

#### Scenario: Speed changes are bounded per tick
- **WHEN** a computer racer's target speed jumps between two ticks
- **THEN** its speed changes by no more than the per-tick acceleration bound

### Requirement: Difficulty shapes speed, offset, missed rings and boost reaction
**MoSCoW:** Should. WHEN a cup starts, THE SYSTEM SHALL derive each computer racer's tuning from the difficulty named by
`VOYAGER_COM_DIFFICULTY`, with a seeded personal variation, so that EASY racers are slower, wander more and miss more rings
than MEDIUM, and HARD racers are the reference racer.

#### Scenario: EASY is slower and wanders further
- **WHEN** an EASY and a HARD computer racer run the same course with the same seed
- **THEN** the EASY racer's average speed is lower and its maximum offset from the line is larger

#### Scenario: HARD never misses a ring
- **WHEN** a HARD computer racer runs a course
- **THEN** it passes every ring on the first attempt

#### Scenario: A boost reaction delay is applied
- **WHEN** a MEDIUM computer racer passes a boost ring
- **THEN** the boost takes effect after the MEDIUM reaction delay, and not before

### Requirement: Computer racers pass rings like human racers
**MoSCoW:** Must. THE SYSTEM SHALL test a computer racer's passes with the same ring test, the same strict order and the same
run as a human racer, and SHALL NOT let a computer racer skip a ring.

#### Scenario: A ring passed on the line is scored in order
- **WHEN** a computer racer crosses ring 0 through its disc and then ring 1
- **THEN** its run has passed two rings, in order, and no ring is passed twice

#### Scenario: A missed ring costs time, not a ring
- **WHEN** a MEDIUM computer racer misses a ring on its first attempt
- **THEN** it returns through the same ring's disc and passes it, and the run passes that ring only once

### Requirement: A stuck computer racer is removed as did-not-finish
**MoSCoW:** Must. IF a computer racer's passed-ring count does not change for 400 ticks while it is on the course, THEN THE
SYSTEM SHALL remove it from the race as did-not-finish, and SHALL leave the other racers unchanged.

#### Scenario: Watchdog removes a stuck racer
- **WHEN** a computer racer makes no progress for 400 ticks
- **THEN** it is removed as did-not-finish and the other computer racers keep racing

### Requirement: Computer racers take no cup points and are never stored
**MoSCoW:** Must. THE SYSTEM SHALL NOT record a computer racer's run in the cup round, SHALL NOT award it placement bonus
or cup points, and SHALL NOT write a computer racer's result to any store.

#### Scenario: A computer racer beats every human
- **WHEN** a computer racer finishes a map before every human racer
- **THEN** every human racer's cup points and placement bonus are the same as they would be without that computer racer

#### Scenario: A computer racer result is not stored
- **WHEN** a cup with computer racers ends
- **THEN** no stored record names a computer racer

### Requirement: Computer racers appear in the ranking table
**MoSCoW:** Should. WHEN a map or a cup ends, THE SYSTEM SHALL list each computer racer in the ranking table as `COM n`,
placed by its own time among all racers. No medal tier is shown for them.

#### Scenario: Table lists computer racers by time
- **WHEN** a map ends with two humans and two computer racers finished
- **THEN** the table lists all four by finish time, and the two computer racers are marked `COM 1` and `COM 2`

### Requirement: Computer racers follow the cup lifecycle
**MoSCoW:** Must. THE SYSTEM SHALL top up the grid to `VOYAGER_COM_COUNT` at each map start, SHALL NOT add or remove a
computer racer in the middle of a map, and SHALL remove every computer racer when the cup ends or aborts.

#### Scenario: Top-up at a map start
- **WHEN** a second map starts and a human racer joined during the first map
- **THEN** the grid is topped up to the configured count only if it is below it, and no computer racer is removed

#### Scenario: Removal at cup end
- **WHEN** a cup finishes
- **THEN** every computer racer's body and tab-list entry are removed before the room returns to waiting

#### Scenario: Removal on abort
- **WHEN** the cup is aborted by the operator
- **THEN** every computer racer's body and tab-list entry are removed

### Requirement: Computer racers take no hub handoff and no permission or join events
**MoSCoW:** Must. THE SYSTEM SHALL NOT send a computer racer to a hub, SHALL NOT resolve a permission subject for it, and
SHALL NOT raise a player join event for it.

#### Scenario: Hub handoff ignores computer racers
- **WHEN** a cup ends and racers are sent to a hub
- **THEN** only human racers are sent, and no computer racer is named in the handoff

### Requirement: With no computer racers the server behaves as before
**MoSCoW:** Must. WHILE `VOYAGER_COM_COUNT` is 0, THE SYSTEM SHALL create no computer racer, read no computer racer state
and produce the same cup transcript as before this capability existed.

#### Scenario: Golden transcripts are unchanged
- **WHEN** the existing cup-session golden transcripts are replayed with `VOYAGER_COM_COUNT` of 0
- **THEN** each transcript is byte-identical to its committed file

### Requirement: Computer racer finishes are announced once in chat
**MoSCoW:** Could. WHEN a computer racer finishes a map, THE SYSTEM COULD send every human racer one chat line naming it, in
the player's language (de_DE or en_US, German with informal "du"), and SHALL NOT send a chat line for any ring pass.

#### Scenario: One line per finish
- **WHEN** `COM 2` finishes a map
- **THEN** each human racer receives one line naming `COM 2`, and no ring pass produces a line
