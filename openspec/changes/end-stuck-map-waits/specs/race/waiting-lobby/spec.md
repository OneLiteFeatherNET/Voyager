# Spec Delta

## Purpose

A map must not wait five minutes for one racer. A grace window ends a map once the first racer has finished and the others have had
a fair time, a racer may retire from a map, and a racer who joins a running cup is told how long until their map.

## REMOVED Requirements

### Requirement: The map time limit stays at the cup's race cap

## MODIFIED Requirements

### Requirement: Racers who join a running cup wait for the next map
**MoSCoW:** Must. WHILE a cup is running, THE SYSTEM SHALL NOT move a joining racer into a race and SHALL tell the racer
that the cup is running, that they join at the next map, and how long that is as an upper bound. The racer enters the next map
when it starts.

#### Scenario: A racer joins during a map
- **WHEN** a racer joins while a map's race phase is running
- **THEN** the racer is told the cup is running, holds no run, is told the upper bound until their map (for example "up to 4 min"),
  and is moved into the next map when it starts

## ADDED Requirements

### Requirement: A map ends after a grace window once a racer has finished
**MoSCoW:** Should. WHEN the first racer finishes a map, THE SYSTEM SHALL start a grace window of `max(30 seconds, 1.5 × the map's
reference time)`, and WHEN the window ends THE SYSTEM SHALL score each racer still racing as DNF and end the map. The map's race cap
of `RaceTimings.race` stays the hard limit; the window never extends the map.

#### Scenario: The first finisher starts the window
- **WHEN** the first racer finishes a map with a reference time of 60 seconds
- **THEN** the grace window is 90 seconds, and the map ends at the earlier of the window's end and the race cap

#### Scenario: A slow racer is scored DNF at the window's end
- **WHEN** the window ends and one racer is still racing
- **THEN** that racer's map is scored DNF with its ring points and no medal points, and the map ends for everyone

#### Scenario: Everyone finishes before the window ends
- **WHEN** every racer finishes before the window ends
- **THEN** the map ends at the last finish, as it does today, and no racer is scored DNF

### Requirement: A racer may retire from a map
**MoSCoW:** Should. WHILE a racer is racing a map, THE SYSTEM SHALL let the racer retire with the retire command `/retire` or the retire
item in the hotbar. THE SYSTEM SHALL score a retired run as DNF through the existing DNF path, and SHALL NOT remove the racer from the
cup.

#### Scenario: A racer retires mid-map
- **WHEN** a racer who has not finished types `/retire` during a map
- **THEN** the racer's map is scored DNF with its ring points and no medal points, the racer is between maps, and the other racers keep racing

#### Scenario: A finished racer cannot retire
- **WHEN** a racer who has already finished the map uses the retire item
- **THEN** nothing is scored again and the item does nothing

#### Scenario: Retire is refused outside a map
- **WHEN** a racer in the lobby types `/retire`
- **THEN** the command is refused with a translated line
