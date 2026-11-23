## MODIFIED Requirements

### Requirement: `/leaderboard` lists the best times of the current layout
**MoSCoW:** Should. WHEN a racer runs `/leaderboard <map>`, THE SYSTEM SHALL list the top ten personal bests for that map's
current layout in ranked mode, fastest first. Anyone may run it; no permission node is required. The reply is a plain chat message,
not clickable. WHEN the caller has a ranked personal best on that layout and is outside the top ten, THE SYSTEM SHALL append one
line with the caller's place and the number of racers with a best, and SHALL add the percentile only when the pool holds at least
50 racers with a best.

#### Scenario: a map with no runs
- **WHEN** the leaderboard is asked for a map with no recorded finish
- **THEN** the reply says that the map has no times yet

#### Scenario: a caller outside the top ten in a large pool
- **WHEN** the caller is 34th of 212 racers with a best on the layout
- **THEN** the reply lists the top ten and then "You: #34 of 212, top 16%"

#### Scenario: a caller outside the top ten in a small pool
- **WHEN** the caller is 11th of 12 racers with a best
- **THEN** the reply lists the top ten and appends "You: #11 of 12" with no percentile

#### Scenario: a caller inside the top ten
- **WHEN** the caller is 3rd of 212
- **THEN** the reply lists the top ten and no own line is added

#### Scenario: a caller with no best
- **WHEN** the caller has no ranked personal best on the layout
- **THEN** the reply lists the top ten and no own line is added
