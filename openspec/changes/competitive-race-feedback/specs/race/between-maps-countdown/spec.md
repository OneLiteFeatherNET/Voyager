## ADDED Requirements

### Requirement: The boss bar counts down to the next map's GO
**MoSCoW:** Must. WHILE a racer waits between two maps of a cup, from the finish of a map until the GO of the next map, THE
SYSTEM SHALL show the boss bar with the title "Next map in N s" (German "Nächste Map in N s"), where N is the whole seconds
left until that GO, rounded up. THE SYSTEM SHALL NOT show this title before the first map or after the last map.

#### Scenario: Full wait after a map
- **WHEN** a map ends and the next map's lobby lasts 20 seconds after an END of 8 seconds
- **THEN** the boss bar reads "Next map in 28 s" at the start of the END, and the number falls by one each second

#### Scenario: Count matches the GO
- **WHEN** N reads 1
- **THEN** the next map's GO follows within one second

#### Scenario: First map
- **WHEN** the first map of a cup is about to start
- **THEN** no "Next map in" title is shown

### Requirement: A standings sidebar lists who has finished the map and by how much
**MoSCoW:** Should. WHILE a racer waits between two maps, THE SYSTEM SHALL show a sidebar that lists the racers who played the
map that has just ended: the finishers first, in order of finish time, each with the time and the gap to the leader; then the
racers who did not finish, marked as such. THE SYSTEM SHALL hold at most fifteen lines and SHALL show "+ n more" when there
are more racers than lines. THE SYSTEM SHALL remove the sidebar at the GO of the next map.

#### Scenario: Three finishers and one DNF
- **WHEN** three racers finish a map at 61.2 s, 62.0 s and 63.5 s and one racer does not finish
- **THEN** the sidebar lists the three finishers in that order with "+0.0 s", "+0.8 s" and "+2.3 s", then the DNF racer

#### Scenario: More racers than lines
- **WHEN** twenty racers played the map
- **THEN** the sidebar holds fifteen lines and its last line reads "+ 5 more"

### Requirement: The wait's last three seconds carry the owner's title and pling
**MoSCoW:** Must. WHILE N is three or fewer, THE SYSTEM SHALL show the title of the wait for each second and play a pling. THE
PLING SHALL be a sample that is none of the countdown beat, the ring bell or the lobby chime, so that each sound means one thing
(`race-feedback`). This requirement is blocked until the owner answers open question 1 in the design: the start countdown
already shows digits and the beat in those same seconds.

#### Scenario: A pling is not the countdown beat
- **WHEN** the pling plays in the last three seconds of the wait
- **THEN** its sample is not the countdown beat, the ring bell or the lobby chime
