## ADDED Requirements

### Requirement: The boss bar counts down to the next map's GO
**MoSCoW:** Must. WHILE a human racer waits between two maps of a cup, from the end of a map until the GO of the next map, THE
SYSTEM SHALL show the boss bar with the title "Next map in N s" (German "Nächste Map in N s"), where N is the whole seconds left
until that GO, rounded up. THE SYSTEM SHALL NOT show this title before the first map or after the last map.

#### Scenario: Full wait after a map
- **WHEN** a map's game phase ends and the next map's lobby lasts 20 seconds after an END of 8 seconds
- **THEN** the boss bar reads "Next map in 28 s" at the end of the phase, and the number falls by one each second

#### Scenario: Count matches the GO
- **WHEN** N reads 1
- **THEN** the next map's GO follows within one second

#### Scenario: First map
- **WHEN** the first map of a cup is about to start
- **THEN** no "Next map in" title is shown

### Requirement: A racer already in the next map sees the wait's boss bar and sidebar there
**MoSCoW:** Must. WHILE a racer stands in the next map's world before the wait has begun for the cup, THE SYSTEM SHALL show the
boss bar with the title "Waiting for <n> racers" (German "Warte auf <n> Racer"), where n is the number of racers still on the
previous map, and SHALL show the standings sidebar. WHEN the previous map's game phase ends, THE SYSTEM SHALL switch the title to
"Next map in N s" for every racer.

#### Scenario: Early finisher in the next world
- **WHEN** a racer is placed on the next map while two others still race the previous map
- **THEN** the racer's boss bar reads "Waiting for 2 racers" and the standings sidebar is shown

#### Scenario: The phase ends
- **WHEN** the previous map's game phase ends
- **THEN** the same racer's boss bar reads "Next map in N s"

### Requirement: The standings sidebar lists everyone who played the map, in a fixed order
**MoSCoW:** Should. WHILE a racer waits, THE SYSTEM SHALL show a sidebar that lists every racer who played the previous map,
computer racers included, in this order: finishers by finish time, each with the time and the gap to the leader; then racers
still racing, marked as racing; then racers who did not finish, marked as such. THE SYSTEM SHALL hold at most fifteen lines and
SHALL show "+ n more" when there are more racers than lines.

#### Scenario: Three finishers, one still racing, one DNF
- **WHEN** three racers have finished the previous map at 61.2 s, 62.0 s and 63.5 s, one is still racing and one did not finish
- **THEN** the sidebar lists the finishers with "+0.0 s", "+0.8 s" and "+2.3 s", then the racing racer, then the DNF racer

#### Scenario: More racers than lines
- **WHEN** twenty racers played the map
- **THEN** the sidebar holds fifteen lines and its last line reads "+ 5 more"

### Requirement: The standings sidebar is live during the wait and removed at the next GO
**MoSCoW:** Should. WHILE a racer waits, THE SYSTEM SHALL recompute the standings sidebar each second, so that a racer still racing
becomes a finisher on the second their finish is recorded, and SHALL remove the sidebar at the next map's GO.

#### Scenario: A racer finishes during the wait
- **WHEN** a racer still marked as racing finishes the previous map in the first second of a wait
- **THEN** the sidebar moves that racer to the finishers within one second

#### Scenario: GO
- **WHEN** the next map's GO tick is reached
- **THEN** the sidebar is removed

### Requirement: The wait's title and the start countdown do not change
**MoSCoW:** Must. THE SYSTEM SHALL keep the start countdown's digits, title and beat in the last three seconds before GO as they are
today, and SHALL NOT add a title or a sound to those seconds for the wait.

#### Scenario: Last three seconds
- **WHEN** N reads 3, 2 or 1 before the next map's GO
- **THEN** the boss bar shows the count, and the start countdown's digit title and beat are the only title and sound in that second
