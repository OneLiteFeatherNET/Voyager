# Spec Delta

## Purpose

Defines what happens to a racer who leaves the vertical bounds of their world or lands during a map's race, where they
return, what the return costs them, and what they are told. It keeps a racer's run honest without making a mistake
cost more time or points than it has to.

## ADDED Requirements

### Requirement: A racer below the floor or above the ceiling is reset
**Priority:** MoSCoW Must
THE SYSTEM SHALL reset a racer who holds an unfinished run and whose vertical position is below the floor or above the
ceiling of the dimension of their world, during the race phase of a map. The floor is the dimension's minimum height and
the ceiling is that minimum plus the dimension's height.

#### Scenario: Racer falls below the floor
- **WHEN** a racer with an unfinished run during the race phase is below the floor of their world's dimension
- **THEN** the system resets that racer

#### Scenario: Racer is exactly on the floor
- **WHEN** a racer with an unfinished run during the race phase is at the floor of their world's dimension
- **THEN** the system does not reset that racer

#### Scenario: Racer rises above the ceiling
- **WHEN** a racer with an unfinished run during the race phase is above the ceiling of their world's dimension
- **THEN** the system resets that racer

### Requirement: A racer who lands during the race is reset
**Priority:** MoSCoW Must
THE SYSTEM SHALL reset a racer who was gliding on the previous race tick, is on the ground and is no longer gliding,
while the racer holds an unfinished run.

#### Scenario: Racer lands mid-course
- **WHEN** a racer who was gliding on the previous race tick is on the ground and no longer gliding, with an unfinished run
- **THEN** the system resets that racer

#### Scenario: Racer takes off
- **WHEN** a racer who was not gliding on the previous race tick starts gliding
- **THEN** the system does not reset that racer

#### Scenario: Racer glides in the air
- **WHEN** a racer who was gliding on the previous race tick is still gliding and above the ground
- **THEN** the system does not reset that racer

### Requirement: A reset returns the racer to the last ring they passed, or to the start
**Priority:** MoSCoW Must
THE SYSTEM SHALL place a reset racer at the centre of the last ring they passed, of any ring type, facing along that
ring's flow normal. When the racer has passed no ring on the current map, the system SHALL place them at the map's spawn
and leave their run unchanged.

#### Scenario: Racer has passed a ring
- **WHEN** a racer is reset and has passed one or more rings on the current map
- **THEN** the racer is placed at the centre of the last ring passed, facing along that ring's flow normal

#### Scenario: Racer has passed no ring
- **WHEN** a racer is reset and has passed no ring on the current map
- **THEN** the racer is placed at the map's spawn, with the run unchanged

### Requirement: A reset keeps every ring already passed
**Priority:** MoSCoW Must
THE SYSTEM SHALL keep every ring the racer has passed, and every point those rings are worth, through a reset.

#### Scenario: Points are kept
- **WHEN** a racer who passed rings 0 to 9 is reset to ring 9
- **THEN** the racer's run still holds rings 0 to 9 as passed, and their points are in the racer's score

#### Scenario: Ring after the target is flown again
- **WHEN** a reset racer passes the next ring after the reset target
- **THEN** that ring scores its points once

### Requirement: A reset costs no time and no points
**Priority:** MoSCoW Must
THE SYSTEM SHALL keep the race clock running through a reset and SHALL add no time and no point penalty to the racer's
result.

#### Scenario: Race clock continues
- **WHEN** a racer is reset during the race
- **THEN** the race clock is not paused and the racer's time continues from the same clock

#### Scenario: Time is not added
- **WHEN** a reset racer finishes the map
- **THEN** their time is the time at which they passed the final ring

### Requirement: A finished run is never reset
**Priority:** MoSCoW Must
THE SYSTEM SHALL NOT reset a racer whose run has passed every ring of the map, whatever the racer does afterwards.

#### Scenario: Finisher leaves the bounds
- **WHEN** a racer who has passed the final ring falls below the floor of their world's dimension
- **THEN** the system does not reset that racer and the racer's result is unchanged

#### Scenario: Finisher lands
- **WHEN** a racer who has passed the final ring lands
- **THEN** the system does not reset that racer

### Requirement: A reset relaunches the racer with the launch of a map start
**Priority:** MoSCoW Must
THE SYSTEM SHALL leave a reset racer gliding, facing the direction the target puts them, and launched with the same
impulse a map start gives: horizontally along the direction they face, and upward. For a reset to the spawn, the facing
is the direction of the map's first ring, as at a map start. The arrival of the racer SHALL NOT count as a landing.

#### Scenario: Reset racer is gliding again
- **WHEN** a racer is reset to a ring
- **THEN** the racer is gliding, faces that ring's flow normal and is launched along it

#### Scenario: Relaunch is not a landing
- **WHEN** a reset racer is gliding on the tick after the reset
- **THEN** the system does not reset that racer

### Requirement: A reset is announced in the tick it is detected
**Priority:** MoSCoW Must
THE SYSTEM SHALL show the reset racer a title that gives the reason and the target, and SHALL play a sound for the reset
that is distinct from the sound of a ring pass, in the same tick the reset is detected.

#### Scenario: Out-of-bounds title
- **WHEN** a racer is reset for leaving the bounds
- **THEN** the racer is shown a title naming the out-of-bounds reason and the target, which is either the number of the ring passed last or the start

#### Scenario: Landing title
- **WHEN** a racer is reset for landing
- **THEN** the racer is shown a title naming the landing and the target

#### Scenario: Reset sound
- **WHEN** a racer is reset
- **THEN** the racer hears the reset sound, and no ring-pass sound is played for that tick

### Requirement: A reset cancels a burning rocket but keeps the cooldown
**Priority:** MoSCoW Should
THE SYSTEM SHALL end an active firework burn of a reset racer and SHALL keep the cooldown of that racer's last boost.

#### Scenario: Burn is cancelled
- **WHEN** a racer is reset while a rocket is burning
- **THEN** the burn ends and no further impulse is applied from it

#### Scenario: Cooldown is kept
- **WHEN** a racer is reset while a boost cooldown is running
- **THEN** the racer cannot boost until that cooldown has ended

### Requirement: Resets happen only during the race, and only for racers who hold a run
**Priority:** MoSCoW Must
THE SYSTEM SHALL apply this capability only in the race phase of a map, and only to a racer who holds a run on that map.
THE SYSTEM SHALL NOT reset or move a racer in the lobby or the end phase, a racer waiting in the waiting room, a racer
held by an aborted cup, or a player who joined the race without a run.

#### Scenario: Racer leaves the bounds in the lobby
- **WHEN** a racer is below the floor during the lobby of a map
- **THEN** the system does not reset that racer

#### Scenario: Waiting racer leaves the bounds
- **WHEN** a racer waiting in the waiting room, with no run, is below the floor of their world's dimension during a race
- **THEN** the system does not reset or move that racer

#### Scenario: Racer who joined mid-race leaves the bounds
- **WHEN** a player who holds no run is below the floor during the race phase
- **THEN** the system does not reset or move that player

### Requirement: Existing map files stay valid
**Priority:** MoSCoW Must
THE SYSTEM SHALL apply this capability to every existing map file without any change to that file.

#### Scenario: Committed map loads unchanged
- **WHEN** the committed catalogue is loaded after this change
- **THEN** every map loads with the same spawn, rings and tuning as before
