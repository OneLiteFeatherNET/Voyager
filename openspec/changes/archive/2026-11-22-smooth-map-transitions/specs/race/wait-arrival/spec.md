## ADDED Requirements

### Requirement: A racer is placed on the next map at the start of the wait, as soon as their own glide-out allows
**MoSCoW:** Must. THE SYSTEM SHALL place each racer on the next map once the wait before that map has started for them: when
they have finished the previous map and their glide-out has ended, or when the previous map's game phase has ended. A racer
placed while other racers still race the previous map SHALL NOT wait for them.

#### Scenario: Early finisher
- **WHEN** a racer finishes a map while two others are still racing it, and the racer's glide-out ends
- **THEN** the racer is placed on the next map, and the two racers still racing are not moved

#### Scenario: Map ends on the last finish
- **WHEN** the last racer finishes and the game phase ends on that tick
- **THEN** each racer is placed once their own glide-out ends, and no placement waits for the lobby

### Requirement: The wait before the first map starts at its first lobby tick
**MoSCoW:** Must. WHEN a cup's first map has no previous map, THE SYSTEM SHALL start its wait at the first lobby tick and place
every racer on the cup then.

#### Scenario: First map
- **WHEN** the first lobby tick of a cup's first map arrives
- **THEN** every racer on the cup is placed on the first map's spawn, facing its first ring, and held until GO

### Requirement: The previous map's race does not move a racer who has been placed
**MoSCoW:** Must. WHILE a racer has been placed on the next map, THE SYSTEM SHALL NOT advance the racer's run on the previous map,
SHALL NOT draw the previous map's HUD or guide line for them, and SHALL keep their run for scoring when the phase ends.

#### Scenario: Scoring is kept
- **WHEN** a racer placed on the next map has a finished run on the previous map
- **THEN** the run is scored at the end of the previous map's phase with the finish time it had

#### Scenario: No race drawn in the next map
- **WHEN** a placed racer is in the next map's world
- **THEN** the previous map's guide line is not sent to them

### Requirement: GO does not move a racer who is already placed on the map
**MoSCoW:** Must. WHEN the GO tick of a map is reached, THE SYSTEM SHALL place only the racers who are not yet placed on that map.

#### Scenario: Racer placed before GO
- **WHEN** a racer was placed at the start of the wait
- **THEN** the GO tick does not move them again or reset their position

### Requirement: A racer who joins during a wait is placed when their preload completes
**MoSCoW:** Should. WHEN a racer joins a cup during the wait before a map, THE SYSTEM SHALL place them on that map once the
preload around the spawn has completed.

#### Scenario: Joining during a wait
- **WHEN** a racer joins a cup during a wait and the preload around the spawn completes after 2 seconds
- **THEN** the racer is placed on that map on the tick the preload completes
