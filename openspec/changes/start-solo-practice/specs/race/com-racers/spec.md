# Spec Delta

## Purpose

A solo practice start has no human opponent, so it fills its grid with computer racers. The fill for a RACE start keeps its
rule. The "no computer racers" rule keeps its meaning for every cup except a practice start, which has its own grid size.

## MODIFIED Requirements

### Requirement: Computer racers fill the grid at cup start
**MoSCoW:** Must. WHEN a RACE cup starts with at least its minimum of human racers (`VOYAGER_MIN_PLAYERS` when set, otherwise
`GameMode.minimumPlayers()`), THE SYSTEM SHALL add computer racers to the start grid until the grid holds `VOYAGER_COM_COUNT`
racers in total, humans included.

#### Scenario: Grid filled to the configured count
- **WHEN** a RACE cup starts with two human racers and `VOYAGER_COM_COUNT` is 5
- **THEN** the grid holds five racers, of which three are computer racers

#### Scenario: No computer racers when the count is zero
- **WHEN** a RACE cup starts with two human racers and `VOYAGER_COM_COUNT` is 0
- **THEN** the grid holds two racers and no computer racer is created

#### Scenario: Humans already fill the grid
- **WHEN** a RACE cup starts with 16 human racers and `VOYAGER_COM_COUNT` is 5
- **THEN** no computer racer is added

### Requirement: With no computer racers the server behaves as before
**MoSCoW:** Must. WHILE `VOYAGER_COM_COUNT` is 0 and the cup is not a practice start, THE SYSTEM SHALL create no computer racer,
read no computer racer state and produce the same cup transcript as before this capability existed.

#### Scenario: Golden transcripts are unchanged
- **WHEN** the existing cup-session golden transcripts are replayed with `VOYAGER_COM_COUNT` of 0
- **THEN** each transcript is byte-identical to its committed file

## ADDED Requirements

### Requirement: A practice start fills its grid to the practice grid size
**MoSCoW:** Must. WHEN a practice cup starts from a solo wait, THE SYSTEM SHALL fill the start grid with computer racers until it holds
`VOYAGER_PRACTICE_COM_COUNT` racers in total, whatever `VOYAGER_COM_COUNT` is. The practice grid size defaults to 4 (one human and
three computer racers), and a value below 1 is refused by the configuration check.

#### Scenario: A solo practice grid
- **WHEN** a practice cup starts from a solo wait with one human racer and `VOYAGER_PRACTICE_COM_COUNT` is 4
- **THEN** the grid holds four racers, of which three are computer racers, and `VOYAGER_COM_COUNT` is not consulted

#### Scenario: A RACE start ignores the practice grid
- **WHEN** a RACE cup starts with two human racers and `VOYAGER_PRACTICE_COM_COUNT` is 4 with `VOYAGER_COM_COUNT` of 0
- **THEN** the grid holds two racers and no computer racer is created
