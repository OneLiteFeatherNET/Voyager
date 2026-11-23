# Spec Delta

## Purpose

A cup starts when the start gate's minimum is met. Today the minimum is two racers for every cup, so a lone racer on a
production server waits forever. This delta makes the gate read the cup's mode minimum, and gives a lone racer a practice run
after a short solo wait. RACE cups keep their two-racer start.

## MODIFIED Requirements

### Requirement: A cup starts only when enough racers are online
**MoSCoW:** Must. THE SYSTEM SHALL NOT start a RACE cup because a racer joined while fewer racers than the minimum are online,
and SHALL take the minimum from the cup's mode (`GameMode.minimumPlayers()`, two for RACE) unless `VOYAGER_MIN_PLAYERS` is set.
WHEN the number of online racers reaches the minimum AND no cup is running, THE SYSTEM SHALL begin the start countdown.

#### Scenario: One racer joins an empty server with a minimum of two
- **WHEN** a single racer joins, no cup is running, and the solo wait has not yet elapsed
- **THEN** no RACE cup starts and the racer is told how many racers are still needed

#### Scenario: The second racer completes the minimum
- **WHEN** a second racer joins while one racer is waiting and the minimum is two
- **THEN** the start countdown begins for the first map of the cup

#### Scenario: An explicit minimum overrides the mode
- **WHEN** `VOYAGER_MIN_PLAYERS` is 1 and a single racer joins an empty server
- **THEN** the start countdown begins for the first map of the cup

## ADDED Requirements

### Requirement: A lone racer starts a practice run after the solo wait
**MoSCoW:** Should. WHILE no cup is running and exactly one racer is waiting, and once the solo wait has elapsed with no second racer
joining, THE SYSTEM SHALL start the shipped practice cup of its first map with the lobby countdown, and SHALL fill the grid with
computer racers as `race/com-racers` specifies for a practice start. The solo wait is 30 seconds by default and is set by
`VOYAGER_SOLO_WAIT_SECONDS`; a value of 0 turns the practice start off.

#### Scenario: A lone racer flies after the wait
- **WHEN** one racer has waited alone for the solo wait with no cup running
- **THEN** the practice cup of its first map starts with the lobby countdown, and the racer is the only human on the grid

#### Scenario: A second racer during the wait gets the ranked start
- **WHEN** a second racer joins before the solo wait has elapsed
- **THEN** no practice cup starts, and the RACE start countdown begins once the minimum of two is met

#### Scenario: The practice start is off at zero
- **WHEN** `VOYAGER_SOLO_WAIT_SECONDS` is 0 and one racer waits alone
- **THEN** no practice cup starts and the racer is told how many racers are still needed, as before

#### Scenario: Leaving ends the practice run
- **WHEN** the only racer of a running practice cup leaves
- **THEN** the cup is aborted without a cup result, and the room returns to waiting

### Requirement: A practice run is never ranked
**MoSCoW:** Must. THE SYSTEM SHALL NOT store a practice run in ranked statistics, SHALL NOT list it in the cup standings or the
ranking table as a ranked result, and SHALL NOT award it cup points.

#### Scenario: A practice finish is not stored
- **WHEN** a human racer finishes a map of the practice cup
- **THEN** no ranked statistic is written for that run, and the racer's place line carries no personal best clause

#### Scenario: A RACE finish is still stored
- **WHEN** a human racer finishes a map of a RACE cup with statistics on
- **THEN** the run is recorded as ranked, as before this change
