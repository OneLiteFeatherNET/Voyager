## ADDED Requirements

### Requirement: A map's points are made of ring points, placement and a time component
**MoSCoW:** Must. THE SYSTEM SHALL award a finisher of a map the sum of their ring points, their placement bonus, and their time
component, and SHALL state the three parts separately in the map's chat breakdown.

#### Scenario: A finished map
- **WHEN** a racer finishes a ranked map
- **THEN** the breakdown names the ring points, the placement bonus and the time component, and their sum is the map's total

### Requirement: The time component is at least a quarter and at most 35 per cent of a map's maximum
**MoSCoW:** Should. THE SYSTEM SHALL set the maximum time component of each map so that it is between 25 and 35 per cent of the
map's maximum total, with the exact share fixed by the owner's decision recorded in the new ADR.

#### Scenario: The shipped course
- **WHEN** the maximum total of `blue_and_red` is computed under the new rule
- **THEN** its time component is between 105 and 147 of its maximum, which is between 25 and 35 per cent of 420

### Requirement: Faster time is worth at least as much as slower time
**MoSCoW:** Must. THE SYSTEM SHALL give a finisher a time component that does not increase with elapsed time, is never negative,
and is never more than the map's maximum time component.

#### Scenario: Two finishers on the same map
- **WHEN** two racers with the same rings and placement finish at 1.0 and 1.5 times the reference time
- **THEN** the faster racer's time component is greater than or equal to the slower racer's, and the slower racer's is not negative

#### Scenario: A finish far over the reference time
- **WHEN** a racer finishes at three times the reference time
- **THEN** the time component is not negative and is not more than the component at the reference time

### Requirement: A DNF earns no time component
**MoSCoW:** Must. WHEN a racer does not finish a map, THE SYSTEM SHALL award the time component zero.

#### Scenario: A racer who does not finish
- **WHEN** a racer does not finish the map
- **THEN** the time component is zero and the medal is DNF

### Requirement: Computer racers take no time component
**MoSCoW:** Must. THE SYSTEM SHALL NOT award a computer racer any time component, and SHALL NOT store one, as for every other cup
point.

#### Scenario: A computer racer finishes first
- **WHEN** a computer racer finishes a map before every human racer
- **THEN** no time component is recorded for that computer racer
