## ADDED Requirements

### Requirement: Each finisher is told their place on the map and the gap to the racer they are chasing
**MoSCoW:** Must. WHEN a racer finishes a map, THE SYSTEM SHALL send the racer a line with their place among the racers who
started the map, out of that number, and the gap in seconds to the racer directly ahead, or for the first place the gap to the
racer directly behind. THE SYSTEM SHALL omit the gap when the racer is alone on the map.

#### Scenario: Second place
- **WHEN** a racer finishes second of five, 1.4 s behind the first
- **THEN** the line reads "Place 2 of 5 · +1.4 s behind <first racer's name>"

#### Scenario: First place
- **WHEN** a racer finishes first of five, 0.8 s ahead of the second
- **THEN** the line reads "Place 1 of 5 · 0.8 s ahead of <second racer's name>"

#### Scenario: Alone on the map
- **WHEN** a racer is the only one who started the map and finishes it
- **THEN** the line reads "Place 1 of 1" and names no other racer

### Requirement: The place counts every racer who started the map, and a racer who did not finish has no place line
**MoSCoW:** Should. THE SYSTEM SHALL count every racer who started the map in the total of a place line, including those who
did not finish. WHEN a racer did not finish, THE SYSTEM SHALL send no place line, and the existing did-not-finish line stays
the only line for that racer.

#### Scenario: A DNF in the total
- **WHEN** five racers start a map, four finish and one does not
- **THEN** the finishers' lines read "out of 5", and the racer who did not finish gets no place line

### Requirement: The personal best clause compares the finish with the stored best
**MoSCoW:** Must. WHEN a racer finishes a map that has a stored best for them, THE SYSTEM SHALL state the clause as "New best!
−<delta> s" when the finish beats the best, as "+<delta> s to your best" when it is slower, and as "Equal to your best" when the
times are equal. WHEN the racer has no stored best for the map, THE SYSTEM SHALL state "New best!" with no delta.

#### Scenario: A new best
- **WHEN** a racer finishes in 59.1 s and the stored best is 61.2 s
- **THEN** the clause reads "New best! −2.1 s"

#### Scenario: Slower than the best
- **WHEN** a racer finishes in 61.8 s and the stored best is 61.2 s
- **THEN** the clause reads "+0.6 s to your best"

#### Scenario: First run on a map
- **WHEN** a racer finishes a map for the first time and no best is stored
- **THEN** the clause reads "New best!" with no delta

### Requirement: The personal best clause appears for ranked play with statistics on, and only then
**MoSCoW:** Must. THE SYSTEM SHALL append the personal best clause to a place line only when statistics are on on the server and
the cup is ranked. In every other case the place line has no clause.

#### Scenario: Ranked cup with statistics on
- **WHEN** a racer finishes a map in a ranked cup with statistics on
- **THEN** the place line carries the personal best clause

#### Scenario: Unranked cup
- **WHEN** a racer finishes a map in an unranked cup with statistics on
- **THEN** the place line carries no personal best clause

### Requirement: With statistics off, no personal best is shown and nothing is claimed about one
**MoSCoW:** Must. WHILE statistics are off on the server, THE SYSTEM SHALL omit the personal best clause from every place line and
SHALL NOT use the words "best", "new best" or "equal to your best" in any line about the map. The place and the gap are still
shown.

#### Scenario: Statistics off
- **WHEN** a racer finishes second of five with statistics off
- **THEN** the line reads "Place 2 of 5 · +1.4 s behind <name>" and nothing about a best time

### Requirement: The personal best is read at the GO and is omitted when it is not back by the finish
**MoSCoW:** Must. WHEN a map's GO is released, THE SYSTEM SHALL start the read of each ranked racer's stored best for the map,
and SHALL NOT wait for it on the server tick. IF the read has not returned when the racer finishes, THEN THE SYSTEM SHALL omit the
personal best clause for that finish, and SHALL NOT state a best it has not read.

#### Scenario: A slow read
- **WHEN** a racer finishes before the read of their best has returned
- **THEN** the line has the place and the gap and no personal best clause
