## ADDED Requirements

### Requirement: A boost ring multiplies a computer racer's target speed after its reaction delay
**Priority:** MoSCoW Must. WHEN a computer racer passes a BOOST ring, THE SYSTEM SHALL multiply the racer's target speed by the
map's boost factor on the tick its reaction delay ends, and SHALL keep the result within the reference-speed cap and the
per-tick acceleration bound. Before that tick, the target speed SHALL NOT change.

#### Scenario: A MEDIUM racer takes the boost after its delay
- **WHEN** a MEDIUM computer racer passes a BOOST ring with a factor of 1.5 at tick 100, and its reaction delay ends at tick 100 plus the MEDIUM delay
- **THEN** its target speed is unchanged until the delay ends, and is multiplied by 1.5 on that tick, within the cap

#### Scenario: A HARD racer at the cap gains nothing
- **WHEN** a HARD computer racer already at the reference-speed cap passes a BOOST ring
- **THEN** its speed stays at the cap

#### Scenario: The map's factor is used, not a constant
- **WHEN** the same computer racer passes a BOOST ring on a map whose factor is 1.2, and then on a map whose factor is 1.5
- **THEN** the target speed after the boost is multiplied by 1.2 on the first map and by 1.5 on the second
