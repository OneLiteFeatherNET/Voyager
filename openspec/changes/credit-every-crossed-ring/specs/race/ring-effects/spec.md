## ADDED Requirements

### Requirement: Every ring crossed on one tick is credited in the order the rings were crossed
**Priority:** MoSCoW Must. WHEN a racer's movement on one tick crosses two or more rings in their order on the course, THE SYSTEM
SHALL credit each of them as passed, in the order of the point at which the movement crosses each ring's plane, and SHALL stop at
the first ring that the movement does not cross. A ring that is crossed later in the course on the same tick SHALL NOT be
credited before an earlier ring that the movement has not crossed.

#### Scenario: Two rings crossed on one tick
- **WHEN** a gliding racer's movement crosses ring 3 and then ring 4 on the same tick
- **THEN** the racer's passed count goes from 2 to 4 on that tick, and neither ring is reported missed

#### Scenario: The second ring is not crossed
- **WHEN** a gliding racer's movement crosses ring 3 and does not cross ring 4
- **THEN** the passed count goes from 2 to 3 and ring 4 stays the next ring

#### Scenario: Rings crossed out of order are not credited
- **WHEN** a gliding racer's movement crosses ring 4's plane at parameter 0.2 and ring 3's plane at parameter 0.6, and ring 3 is the next ring
- **THEN** ring 4 is not credited on that tick, ring 3 is credited, and ring 4 is credited on a later tick when its crossing is next in order
