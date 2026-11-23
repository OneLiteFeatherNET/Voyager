## MODIFIED Requirements

### Requirement: Each sound means one thing, and no two meanings share a sound
**MoSCoW:** Must. THE SYSTEM SHALL play the countdown beat only for a countdown digit, the ring bell or the last-ring chime
only for a ring pass of a plain ring, a boost sample only for a BOOST ring, a slow sample only for a SLOW ring, a checkpoint
sample only for a CHECKPOINT ring, and a different sound for a reset, a DNF and a missed ring.

#### Scenario: A reset does not sound like a countdown
- **WHEN** a racer is reset to a ring or to the start
- **THEN** the reset sound is not the countdown beat sample, at any pitch

#### Scenario: A DNF is quieter than a finished medal
- **WHEN** a map ends as DNF
- **THEN** the DNF sound's volume is below the volume of the medal sound for Diamond, Gold, Silver and Bronze

#### Scenario: A BOOST ring does not sound like a plain ring
- **WHEN** a racer passes a BOOST ring
- **THEN** the sound played is the boost sample, and it is not the ring bell at any pitch

#### Scenario: A SLOW ring does not sound like a BOOST ring
- **WHEN** a racer passes a SLOW ring
- **THEN** the sound played is the slow sample, and it is not the boost sample

#### Scenario: A checkpoint sound is played once per checkpoint
- **WHEN** a racer passes a CHECKPOINT ring
- **THEN** the checkpoint sample is played once, and no ring bell is played for that ring

### Requirement: A missed ring is signalled once, in the action bar and by sound
**MoSCoW:** Should. WHEN a racer passes ring k+1 while ring k is still the next ring expected, THE SYSTEM SHALL report ring k
as missed exactly once, play the missed-ring sound once, and show `Ring <k> missed` in the action bar for 1.5 s, in the warn
colour, after which the flight HUD returns on its next refresh. WHILE the missed-ring line is shown, THE SYSTEM SHALL NOT show
a checkpoint or boost or slow line in its place; such a line is dropped, not queued.

#### Scenario: Skipping one ring
- **WHEN** a racer passes ring 6 directly after ring 4
- **THEN** ring 5 is reported missed once, and the missed-ring sound and the line `Ring 5 missed` are sent once

#### Scenario: A normal run
- **WHEN** a racer passes rings 1 to 35 in order
- **THEN** no ring is reported missed and no missed-ring cue is sent

#### Scenario: The same ring is not reported twice
- **WHEN** a racer passes ring 6 again after a reset to ring 4
- **THEN** ring 5 is not reported missed a second time for the same run

#### Scenario: A checkpoint line does not replace a missed-ring line
- **WHEN** ring 5 is reported missed and, in the same tick, a checkpoint ring is passed
- **THEN** the action bar shows `Ring 5 missed`, and no checkpoint line is shown for that pass

## ADDED Requirements

### Requirement: A checkpoint save is announced in the action bar
**MoSCoW:** Should. WHEN a racer passes a CHECKPOINT ring, THE SYSTEM SHALL show `Checkpoint <n> saved` in the action bar for 1.5 s,
where n is the checkpoint's ordinal in the map, counted from 1, in the OK colour.

#### Scenario: The first checkpoint
- **WHEN** a racer passes the first checkpoint ring of a map
- **THEN** the action bar reads "Checkpoint 1 saved" for 1.5 s

#### Scenario: A reset does not announce a save
- **WHEN** a racer is reset to the last checkpoint passed
- **THEN** no "saved" line is shown for the reset itself

### Requirement: A BOOST or SLOW ring names its effect in the action bar
**MoSCoW:** Should. WHEN a racer passes a BOOST ring, THE SYSTEM SHALL show `Boost` in the action bar for 1.5 s; WHEN a racer passes
a SLOW ring, THE SYSTEM SHALL show `Slow` in the same way. Both use the palette's speed colour.

#### Scenario: A boost ring
- **WHEN** a racer passes a BOOST ring
- **THEN** the action bar reads "Boost" for 1.5 s in the speed colour

#### Scenario: A slow ring
- **WHEN** a racer passes a SLOW ring
- **THEN** the action bar reads "Slow" for 1.5 s in the speed colour
