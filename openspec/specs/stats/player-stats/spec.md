# stats/player-stats Specification

## Purpose
Defines what the server records about players and runs, what `/stats` and `/leaderboard` show, and which racers and modes count.
It is the player-facing contract of this change. Computer racers are never recorded.

## Requirements

### Requirement: Each finished map records every human racer's run
**MoSCoW:** Must. WHEN a map finishes, THE SYSTEM SHALL record for each human racer who held a run: the rings passed, the tick
of each ring, the boosts accepted, the resets, the gliding time, the finish position and time if finished, and the ring points,
placement bonus and medal.

#### Scenario: a racer finishes with two resets and three boosts
- **WHEN** a racer finishes a map after two resets and three accepted boosts
- **THEN** the stored result has those counts, a split for every ring, the finish time, and the medal of the finish

#### Scenario: a racer does not finish
- **WHEN** a racer does not reach the last ring before the map ends
- **THEN** the stored result has no finish time and the medal DNF

### Requirement: Each cup end records the cup's results and medals
**MoSCoW:** Must. WHEN a cup finishes, THE SYSTEM SHALL record for each human racer the cup's final position, total points,
maps finished, best time and the number of medals per tier.

#### Scenario: medals across a cup
- **WHEN** a racer wins a diamond on one map and a gold on another
- **THEN** the cup result counts one diamond and one gold

### Requirement: The personal best of a map keeps the fastest time
**MoSCoW:** Must. WHEN a racer finishes a map, THE SYSTEM SHALL keep the fastest finish time for that map, layout and mode, and
on a tie SHALL keep the earlier finish.

#### Scenario: a slower run later
- **WHEN** a racer finished in 61.2 s and later in 63.0 s on the same layout
- **THEN** the personal best stays at 61.2 s

#### Scenario: a faster run later
- **WHEN** the same racer later finishes in 59.8 s
- **THEN** the personal best becomes 59.8 s and points at that run

### Requirement: The profile counts ranked play only
**MoSCoW:** Must. THE SYSTEM SHALL count cups played, cup wins, medals per tier, races played, races won, rings passed, boosts,
resets and flight time in the player profile only for modes where the mode is ranked, while every mode is kept per run.

#### Scenario: a practice run
- **WHEN** a racer finishes a practice map
- **THEN** the run is stored, and the profile's races played, medals and total flight time do not change

### Requirement: `/stats` shows a player's profile
**MoSCoW:** Must. WHEN a racer runs `/stats [player]`, THE SYSTEM SHALL reply in chat with that player's profile, and the reply
SHALL be sent from the game loop. Without an argument it answers for the sender. Anyone may run it.

#### Scenario: the profile content
- **WHEN** a player with recorded ranked runs runs `/stats`
- **THEN** the reply shows cups played, cup wins, medals per tier (the total of each tier), total flight time and the player's
  personal bests, as a plain chat message that is not clickable, in de_DE (informal "du") and en_US

#### Scenario: a new player
- **WHEN** a player who has never finished a map runs `/stats`
- **THEN** the reply says that no statistics are recorded yet

#### Scenario: a returning player
- **WHEN** a player with recorded races runs `/stats`
- **THEN** the reply shows cups played, cup wins, medals per tier, total flight time and the personal bests

#### Scenario: a player looked up by name
- **WHEN** a racer runs `/stats Alex`
- **THEN** the profile of the player whose `player.last_known_name` is `Alex`, compared case-insensitively, is shown; if several players match, the one with the most recent `last_seen` is shown

#### Scenario: an unknown name
- **WHEN** a racer runs `/stats` with a name that no player has as last known name
- **THEN** the reply says that no player was found

### Requirement: `/leaderboard` lists the best times of the current layout
**MoSCoW:** Should. WHEN a racer runs `/leaderboard <map>`, THE SYSTEM SHALL list the top ten personal bests for that map's
current layout in ranked mode, fastest first. Anyone may run it; no permission node is required. The reply is a plain chat message,
not clickable.

#### Scenario: a map with no runs
- **WHEN** the leaderboard is asked for a map with no recorded finish
- **THEN** the reply says that the map has no times yet

### Requirement: Computer racers are never recorded
**MoSCoW:** Must. THE SYSTEM SHALL NOT store a result, a split, a personal best, a profile count or a medal for a computer racer,
and SHALL store only the count of computer racers on the race session.

#### Scenario: a race with two humans and three computer racers
- **WHEN** a map finishes with two human racers and three computer racers
- **THEN** two results are stored, and the race session records a bot count of three

### Requirement: A read answers within two seconds or says it cannot
**MoSCoW:** Must. WHILE a `/stats` or `/leaderboard` read is running, THE SYSTEM SHALL keep the game loop running, and IF the read
does not finish within two seconds, THEN THE SYSTEM SHALL reply that statistics are unavailable right now.

#### Scenario: a slow database
- **WHEN** the database takes longer than two seconds to answer
- **THEN** the racer receives the unavailable message, and no tick is delayed
