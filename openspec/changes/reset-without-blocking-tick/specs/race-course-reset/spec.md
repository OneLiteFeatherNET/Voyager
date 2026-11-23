## ADDED Requirements

### Requirement: A reset is applied once its target chunks are present, and the racer is held until then
**Priority:** MoSCoW Must. WHEN a racer is reset, THE SYSTEM SHALL start loading the chunks around the reset target without
waiting for them on the server tick. WHILE those chunks are not all present, THE SYSTEM SHALL hold the racer in place, not
gliding, with no ring credited and no ring effect applied. THE SYSTEM SHALL apply the reset on the first tick after every
target chunk is present.

#### Scenario: The tick does not wait for the chunks
- **WHEN** a racer is reset to a checkpoint whose chunks are not loaded
- **THEN** the tick that detected the reset returns at once, and the other racers' ticks run as usual

#### Scenario: The racer is held until the chunks are present
- **WHEN** a reset is pending and two of its target chunks are still loading
- **THEN** the racer stays at the position it was held at, is not gliding, and is not credited with any ring

#### Scenario: The reset is applied on the tick after the last chunk
- **WHEN** the last target chunk of a pending reset becomes present on tick 500
- **THEN** the reset is applied on tick 501 and the racer is placed gliding at the target

### Requirement: A second reset during a hold replaces the pending target
**Priority:** MoSCoW Must. WHILE a reset is pending for a racer, IF a second reset is detected for the same racer, THEN THE
SYSTEM SHALL replace the pending target with the target of the second reset, SHALL keep the racer held, and SHALL show the title
and play the sound of the second reset on the tick it is detected.

#### Scenario: Landing during a hold
- **WHEN** a racer who is held for a pending reset is detected as out of bounds on the next tick
- **THEN** the pending target is replaced by the out-of-bounds target, the racer stays held, and the out-of-bounds title is shown
  on that tick
