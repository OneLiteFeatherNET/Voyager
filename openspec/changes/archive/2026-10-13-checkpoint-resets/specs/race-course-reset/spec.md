# Spec Delta

## Purpose

Changes where a reset sends a racer, what the racer keeps, how they arrive and when a landing counts. A reset returns a
racer to the last checkpoint they passed, or to the spawn; the racer keeps only the rings up to that point; the racer
arrives with the elytra deployed and no impulse; and a landing counts only after a short airborne grace and outside a
cooldown that follows a reset. Out-of-bounds resets are not changed by the grace or the cooldown.

## MODIFIED Requirements

### Requirement: A racer who lands during the race is reset
**Priority:** MoSCoW Must
THE SYSTEM SHALL reset a racer who was gliding on the previous race tick, is on the ground and is no longer gliding,
while the racer holds an unfinished run, provided that the racer has been airborne for at least 20 consecutive race ticks
before this tick and that no reset of the racer happened in the 40 race ticks before this tick.

#### Scenario: Racer lands mid-course
- **WHEN** a racer who was gliding on the previous race tick is on the ground and no longer gliding, with an unfinished run,
  after at least 20 consecutive airborne race ticks and outside the cooldown of a reset
- **THEN** the system resets that racer

#### Scenario: Racer lands before the airborne grace has passed
- **WHEN** a racer who was gliding on the previous race tick is on the ground and no longer gliding, but has been airborne
  for fewer than 20 consecutive race ticks
- **THEN** the system does not reset that racer

#### Scenario: Racer lands during the cooldown of a reset
- **WHEN** a racer who was gliding on the previous race tick is on the ground and no longer gliding, less than 40 race ticks
  after a reset of that racer
- **THEN** the system does not reset that racer

#### Scenario: Racer takes off
- **WHEN** a racer who was not gliding on the previous race tick starts gliding
- **THEN** the system does not reset that racer

#### Scenario: Racer glides in the air
- **WHEN** a racer who was gliding on the previous race tick is still gliding and above the ground
- **THEN** the system does not reset that racer

### Requirement: A reset is announced in the tick it is detected
**Priority:** MoSCoW Must
THE SYSTEM SHALL show the reset racer a title that gives the reason and the target, and SHALL play a sound for the reset
that is distinct from the sound of a ring pass, in the same tick the reset is detected.

#### Scenario: Out-of-bounds title
- **WHEN** a racer is reset for leaving the bounds
- **THEN** the racer is shown a title naming the out-of-bounds reason and the target, which is either the number of the
  checkpoint ring the racer is returned to or the start

#### Scenario: Landing title
- **WHEN** a racer is reset for landing
- **THEN** the racer is shown a title naming the landing and the target

#### Scenario: Reset sound
- **WHEN** a racer is reset
- **THEN** the racer hears the reset sound, and no ring-pass sound is played for that tick

### Requirement: A reset costs no time and no points
**Priority:** MoSCoW Must
THE SYSTEM SHALL keep the race clock running through a reset and SHALL add no time and no point penalty of its own to the
racer's result. A ring removed by a reset (see "A reset keeps the rings up to its target") scores its points again when the
racer passes it again.

#### Scenario: Race clock continues
- **WHEN** a racer is reset during the race
- **THEN** the race clock is not paused and the racer's time continues from the same clock

#### Scenario: Time is not added
- **WHEN** a reset racer finishes the map
- **THEN** their time is the time at which they passed the final ring

#### Scenario: Removed rings score once more
- **WHEN** a reset racer passes again a ring that the reset removed
- **THEN** that ring scores its points once, as it does for any first pass

## REMOVED Requirements

### Requirement: A reset returns the racer to the last ring they passed, or to the start
**Reason**: The target is the last checkpoint passed, not the last ring of any type. See the replacement below.

### Requirement: A reset keeps every ring already passed
**Reason**: A reset keeps the rings up to its target and no others, so a racer cannot skip rings. See the replacement below.

### Requirement: A reset returns the racer without an impulse
**Reason**: The racer is now placed gliding (the flag is set), where the replaced requirement said the racer is not
gliding. The replacement keeps the rule that no impulse is sent and states the glide. See the ADDED requirement below.

## ADDED Requirements

### Requirement: A reset returns the racer to the last checkpoint passed, or to the start
**Priority:** MoSCoW Must
THE SYSTEM SHALL place a reset racer at the centre of the last `CHECKPOINT` ring they passed on the current map, facing along
that ring's flow normal. When the racer has passed no `CHECKPOINT` ring on the current map, THE SYSTEM SHALL place them at the
map's spawn.

#### Scenario: Racer has passed a checkpoint
- **WHEN** a racer is reset and has passed one or more `CHECKPOINT` rings on the current map
- **THEN** the racer is placed at the centre of the last `CHECKPOINT` ring passed, facing along that ring's flow normal

#### Scenario: Racer has passed no checkpoint
- **WHEN** a racer is reset and has passed no `CHECKPOINT` ring on the current map, even if they passed other rings
- **THEN** the racer is placed at the map's spawn

#### Scenario: Map with no checkpoint rings
- **WHEN** a racer is reset on a map that has no `CHECKPOINT` ring
- **THEN** the racer is placed at the map's spawn

### Requirement: A reset keeps the rings up to its target
**Priority:** MoSCoW Must
THE SYSTEM SHALL keep the rings the racer passed up to and including the reset target, and their points. THE SYSTEM SHALL
remove the rings passed after the target, and their points, so that the racer passes them again in order. A spawn reset keeps
no ring. The race clock is not changed by the removal.

#### Scenario: Rings up to the checkpoint are kept
- **WHEN** a racer who passed rings 0 to 9 is reset to the `CHECKPOINT` ring 4
- **THEN** the racer's run holds rings 0 to 4 as passed, and the points of rings 0 to 4 are in the racer's score

#### Scenario: Rings after the checkpoint are passed again
- **WHEN** a reset racer who was reset to ring 4 passes ring 5 again
- **THEN** ring 5 scores its points once, and the racer's score counts it once

#### Scenario: Spawn reset keeps no ring
- **WHEN** a racer who passed rings 0 to 9 with no checkpoint is reset to the spawn
- **THEN** the racer's run holds no ring as passed, and the racer's score holds no ring points

### Requirement: A reset places the racer gliding, without an impulse
**Priority:** MoSCoW Must
THE SYSTEM SHALL place a reset racer at the reset target, facing the direction the target gives, with the elytra and the
rockets in hand, and SHALL set the racer gliding. THE SYSTEM SHALL NOT send an impulse. The only velocity sent by a reset is
a zero, which zeroes the racer's velocity so that a fall is not carried into the target. For a reset to the spawn, the
facing is the direction of the map's first ring, as at a map start.

#### Scenario: Reset racer is placed gliding with the equipment
- **WHEN** a racer is reset to a checkpoint ring
- **THEN** the racer is at that ring's centre, faces that ring's flow normal, holds the elytra and the rockets, and is gliding

#### Scenario: Reset zeroes the racer's velocity
- **WHEN** a racer is reset
- **THEN** the racer's velocity is zero on that tick, so a fall is not carried into the target; no other velocity is sent

#### Scenario: Reset adds no impulse
- **WHEN** a racer is reset and the reset is applied
- **THEN** no velocity other than zero is sent for that reset

#### Scenario: Reset to the spawn
- **WHEN** a racer is reset to the spawn
- **THEN** the racer is placed facing the first ring, with the equipment and the gliding flag set, as at a map start; if the
  spawn is on the ground the flag ends on the first tick the client reports the ground, and the racer takes off by
  deploying the elytra (design D3, option S1, pending owner confirmation)

### Requirement: A reset starts a landing grace and a cooldown
**Priority:** MoSCoW Must
THE SYSTEM SHALL count the consecutive race ticks a racer is not on the ground, and SHALL start the count again from zero on
every tick the racer is on the ground. A reset sets a cooldown of 40 race ticks for that racer, during which no landing is
counted. The grace and the cooldown do not apply to a reset for leaving the bounds.

#### Scenario: Airborne count restarts on the ground
- **WHEN** a racer has been airborne for 15 race ticks and then stands on the ground for one tick
- **THEN** the airborne count is zero from that tick

#### Scenario: Out-of-bounds reset during a cooldown
- **WHEN** a racer is below the floor during the cooldown of a reset
- **THEN** the system resets that racer for leaving the bounds
