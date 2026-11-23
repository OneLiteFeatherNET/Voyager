# Spec Delta

## Purpose

An operator can export and erase one player's statistics by `player_id`, and old rows are purged after a defined retention
period. The procedure is a runbook, not code: it removes every row that references the player, in foreign-key order, inside one
transaction, and it states what that does to personal bests and leaderboards.

## ADDED Requirements

### Requirement: An operator can export one player's records by player_id
**MoSCoW:** Should. WHEN an operator asks for a player's records, THE RUNBOOK SHALL give one query per table that references
`voyager.player`, each filtered by `player_id`, and SHALL state which tables and columns the export covers.

#### Scenario: an export covers every referencing table
- **WHEN** an operator follows the export section of the runbook for a `player_id`
- **THEN** the output contains the rows of `player`, `player_statistics`, `race_result`, `race_split`, `cup_result` and
  `map_personal_best` for that player, and no row of another player

### Requirement: An erasure request removes every row that references the player, in one transaction
**MoSCoW:** Must. WHEN an erasure request for a `player_id` is served, THE RUNBOOK SHALL remove the player's rows from
`map_personal_best`, `race_split`, `race_result`, `cup_result`, `player_statistics` and `player`, in that order, inside one
transaction, and SHALL keep `race_session` and `cup_session`, which hold no player key.

#### Scenario: an erasure that would break a foreign key
- **WHEN** the runbook is run in any other order
- **THEN** the runbook states that the transaction fails on the first referencing table, and the first step removes
  `map_personal_best`, which references `race_result`

#### Scenario: an erasure leaves the sessions
- **WHEN** an erasure completes
- **THEN** `race_session` and `cup_session` keep their rows and their counts of humans and bots

### Requirement: An erasure states its effect on bests and leaderboards
**MoSCoW:** Must. THE RUNBOOK SHALL state, for each erasure, that the player's personal bests are removed, that a map's leaderboard
then shows the next best time of another player, and that a race or cup history no longer lists the player.

#### Scenario: a leaderboard after an erasure
- **WHEN** an erased player held the best time of a layout
- **THEN** the next-fastest personal best of that layout is the first row of the leaderboard, and the runbook says so before the
  erasure is run

### Requirement: A rehearsal on a copy precedes the first real erasure
**MoSCoW:** Must. THE RUNBOOK SHALL NOT be marked usable until its erasure and export have been run on a copy of the production
schema, and its rehearsal section SHALL record the row counts removed for each table.

#### Scenario: the rehearsal is recorded
- **WHEN** the runbook is marked usable
- **THEN** its rehearsal section names the copy's schema version, the player used, and the row count per table before and after

### Requirement: A retention period is defined and purged by the runbook
**MoSCoW:** Should. THE RUNBOOK SHALL name one retention period for race, cup and personal-best rows, and SHALL give a purge query
that removes rows older than that period in foreign-key order, with the effect on leaderboards stated as in the erasure requirement.

#### Scenario: a purge with the period not yet decided
- **WHEN** the owner has not decided the period
- **THEN** the runbook names no purge query and says that retention is undecided

### Requirement: Each request has a named owner
**MoSCoW:** Must. THE RUNBOOK SHALL name the role that receives an erasure request, the channel on which it is accepted, and the
number of days within which it is answered, and SHALL NOT name a person.

#### Scenario: a request arrives
- **WHEN** an erasure request arrives
- **THEN** the runbook says who receives it and in how many days it is answered
