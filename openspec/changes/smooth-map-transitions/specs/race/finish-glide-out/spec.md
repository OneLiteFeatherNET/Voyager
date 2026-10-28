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

### Requirement: The finish has a firework and a sound, distinct from the countdown and the ring bell
**MoSCoW:** Must. WHEN a racer passes the last ring, THE SYSTEM SHALL show one firework burst at the finish ring and play a
finish sound, and the finish sound SHALL NOT be the countdown beat, the ring bell or the lobby chime.

#### Scenario: One burst
- **WHEN** a racer passes the last ring
- **THEN** exactly one firework burst is shown at the finish ring, and the finish sound plays once

### Requirement: The stand-down happens at the end of the glide-out, at the spawn of the map just finished
**MoSCoW:** Must. WHEN a finisher's glide-out ends, THE SYSTEM SHALL turn the elytra off, set the velocity to zero and place
the racer at the spawn of the map they finished, without elytra, where they wait until the next map's arrival. THE SYSTEM
SHALL NOT stand down a finisher at the moment the game phase ends.

#### Scenario: The map ends on the last finish
- **WHEN** every racer has finished, so the game phase ends on the tick of the last finish
- **THEN** each finisher keeps gliding until their own glide-out ends, and is then placed at the spawn with the elytra off

#### Scenario: The map ends with a racer still airborne
- **WHEN** the time limit ends with a racer who has not finished still in the air
- **THEN** that racer is stood down on the tick the phase ends, as today

### Requirement: The glide-out completes inside the END window
**MoSCoW:** Should. THE SYSTEM SHALL complete every finisher's glide-out before the END phase that follows the map ends, so that
no racer is still gliding when the next map's lobby begins.

#### Scenario: Production END window
- **WHEN** a finisher passes the last ring at the start of the game phase's end
- **THEN** the glide-out ends within the 8-second END window, before the next map's lobby begins
