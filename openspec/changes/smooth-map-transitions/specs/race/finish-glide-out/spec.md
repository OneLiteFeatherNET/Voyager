## ADDED Requirements

### Requirement: A finisher keeps flying for 2.5 seconds, with speed damped to a quarter
**MoSCoW:** Must. WHEN a racer passes the last ring of a map, THE SYSTEM SHALL keep the racer gliding for 50 ticks (2.5 s)
and SHALL damp the racer's speed each tick, so that at the end of the window the speed is at most a quarter of the speed on
the finish tick. THE SYSTEM SHALL NOT set the speed to zero during the window and SHALL NOT reverse the racer's direction.

#### Scenario: Gentle braking
- **WHEN** a racer passes the last ring at 1.6 blocks per tick
- **THEN** the racer keeps gliding for 50 ticks, and the speed at the end of the window is at most 0.4 blocks per tick

#### Scenario: No hard stop
- **WHEN** the racer's glide-out is in progress at tick 10 of 50
- **THEN** the speed is still above a quarter of the finish speed, and the racer is still gliding

### Requirement: A racer still airborne when the map's time runs out gets the same glide-out
**MoSCoW:** Must. WHEN a map's game phase ends on its time limit, THE SYSTEM SHALL give each racer who is still gliding the same
50-tick damped glide-out, starting on the tick the phase ends. A racer who is not gliding on that tick SHALL NOT receive a
glide-out.

#### Scenario: Airborne at the limit
- **WHEN** the time limit ends while a racer is gliding at 1.2 blocks per tick
- **THEN** the racer glides out for 50 ticks with the same damping as a finisher

#### Scenario: On the ground at the limit
- **WHEN** the time limit ends while a racer stands on the ground
- **THEN** the racer receives no glide-out and is placed as soon as the wait allows

### Requirement: The finish has a firework and a sound, distinct from the countdown and the ring bell
**MoSCoW:** Must. WHEN a racer passes the last ring, THE SYSTEM SHALL show one firework burst at the finish ring and play a
finish sound, and the finish sound SHALL NOT be the countdown beat, the ring bell or the lobby chime.

#### Scenario: One burst
- **WHEN** a racer passes the last ring
- **THEN** exactly one firework burst is shown at the finish ring, and the finish sound plays once

### Requirement: After its glide-out, a racer is held in place until placed on the next map
**MoSCoW:** Must. WHEN a racer's glide-out ends, THE SYSTEM SHALL turn the elytra off, set the velocity to zero and hold the
racer at the position where the glide-out ended, without gliding, until the racer is placed on the next map. THE SYSTEM SHALL
NOT place a finisher at the spawn of the map just finished.

#### Scenario: Finisher waits while others race
- **WHEN** a finisher's glide-out ends while two other racers are still racing the map
- **THEN** the finisher is held in place, not gliding, and the next map's preload runs for them

#### Scenario: Glide-out ends after the map's end
- **WHEN** the game phase ends and a racer's glide-out ends later in the same wait
- **THEN** the racer is held in place until their placement
