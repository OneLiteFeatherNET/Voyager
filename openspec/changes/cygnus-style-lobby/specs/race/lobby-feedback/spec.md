# Spec Delta

## Purpose

The waiting room is heard as well as seen: a note-block pling counts the last seconds of the lobby, a soft sound marks a joining
racer, a distinct sound marks a shortened countdown, and the last three seconds show large digit titles. Each sound keeps one
meaning, as `race-feedback` requires.

## ADDED Requirements

### Requirement: The lobby countdown plays a pling at fixed seconds
**MoSCoW:** Must. WHILE the first map's lobby counts down, THE SYSTEM SHALL play the sound `BLOCK_NOTE_BLOCK_PLING` to each waiting
racer when 10, 5, 3, 2, 1 and 0 seconds remain, with the volume and pitch of this table, from the Cygnus countdown:

| Seconds left | Volume | Pitch |
|---|---|---|
| 10 | 0.5 | 1.000 |
| 5 | 0.6 | 0.944 |
| 3 | 0.7 | 0.667 |
| 2 | 0.75 | 0.630 |
| 1 | 0.85 | 0.561 |
| 0 | 0.9 | 0.500 |

#### Scenario: The pling at ten seconds
- **WHEN** the lobby reaches ten seconds left
- **THEN** each waiting racer hears the pling at volume 0.5 and pitch 1.0

#### Scenario: No pling between the marks
- **WHEN** the lobby reaches seven seconds left
- **THEN** no pling plays for that second

### Requirement: The large digit titles mark the last three seconds
**MoSCoW:** Must. WHEN three, two or one seconds remain before the GO tick, THE SYSTEM SHALL show each waiting racer the large
title of that digit, as `RaceFeedback.countdown` does today, and SHALL show the digit in the strong accent colour of
`race/lobby-hud`.

#### Scenario: The three-second title
- **WHEN** three seconds of the lobby remain
- **THEN** each waiting racer sees the title "3" in the accent colour

### Requirement: A joining racer is marked by a soft sound for the racers already waiting
**MoSCoW:** Should. WHEN a racer joins the waiting room, THE SYSTEM SHALL play the soft sound `ENTITY_ITEM_PICKUP` at volume 0.4
and pitch 1.0 to each racer already waiting, and SHALL NOT play it to the joining racer.

#### Scenario: Another racer joins
- **WHEN** a racer joins a room with one waiting racer
- **THEN** the waiting racer hears the soft sound, and the joining racer does not

### Requirement: A full lobby that shortens the countdown has its own sound
**MoSCoW:** Must. WHEN the full-lobby rule shortens the countdown, THE SYSTEM SHALL play the sound `BLOCK_NOTE_BLOCK_CHIME` at
volume 0.8 and pitch 1.0 once to each waiting racer.

#### Scenario: The full lobby sound
- **WHEN** the sixteenth racer joins and the countdown is shortened to ten seconds
- **THEN** each waiting racer hears the chime once

### Requirement: No lobby sound shares a meaning with a race sound
**MoSCoW:** Must. THE SYSTEM SHALL play none of the lobby sounds of this capability as the ring bell, the last-ring chime, the reset
sound, the DNF sound, the missed-ring sound or the countdown beat sample of `race-feedback`. The pling marks the lobby's countdown
and is not the race's countdown beat.

#### Scenario: The lobby pling is not the race beat
- **WHEN** the race countdown beat and the lobby pling are compared
- **THEN** they are different sounds, and the test named for this requirement fails if they become the same sample
