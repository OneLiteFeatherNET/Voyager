## ADDED Requirements

### Requirement: The first tick after a reset counts no ring and no effect
**Priority:** MoSCoW Must. WHEN a racer is reset, THE SYSTEM SHALL forget the racer's position from before the reset, so that
the first tick after the reset is measured from the reset position. On that tick THE SYSTEM SHALL NOT credit a ring and SHALL NOT
apply a ring effect.

#### Scenario: A reset across a ring plane
- **WHEN** a racer is reset to the last checkpoint, and the reset position lies on the far side of a ring plane from the position
  the racer had before the reset
- **THEN** the teleport between the two positions credits no ring and applies no ring effect

#### Scenario: The next tick is measured from the reset position
- **WHEN** the tick after a reset moves the racer forward by 1.6 blocks
- **THEN** the movement is measured from the reset position, and the racer's progress follows the movement from that position
