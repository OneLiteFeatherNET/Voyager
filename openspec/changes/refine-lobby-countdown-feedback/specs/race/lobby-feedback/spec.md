# Spec Delta

## Purpose

The full-lobby sound of the waiting room becomes the Cygnus chime, and the rule that no lobby sound is a race sound gets one
named exception for that chime. The countdown plings, the join sound and the large digit titles do not change.

## MODIFIED Requirements

### Requirement: A full lobby that shortens the countdown has its own sound
**MoSCoW:** Must. WHEN the full-lobby rule shortens the countdown, THE SYSTEM SHALL play the sound `BLOCK_NOTE_BLOCK_CHIME` at
volume 0.8 and pitch 1.0 once to each waiting racer. THE SYSTEM SHALL NOT play `BLOCK_NOTE_BLOCK_XYLOPHONE` for this event.

#### Scenario: The full lobby sound
- **WHEN** the sixteenth racer joins and the countdown is shortened to ten seconds
- **THEN** each waiting racer hears the chime once, at volume 0.8 and pitch 1.0

#### Scenario: No xylophone
- **WHEN** the full-lobby sound is compared with the sound the lobby used before this change
- **THEN** the sound is `block.note_block.chime`, and the sound key `block.note_block.xylophone` is not used by the lobby

### Requirement: No lobby sound shares a meaning with a race sound
**MoSCoW:** Must. THE SYSTEM SHALL play none of the lobby sounds of this capability as the ring bell, the last-ring chime, the reset
sound, the DNF sound, the missed-ring sound or the countdown beat sample of `race-feedback`, with one exception: the full-lobby
sound is `block.note_block.chime`, which is also the race's reset sound. The exception is allowed because the owner chose the
Cygnus chime for the full lobby; the two sounds are heard in different phases (a full waiting room, and a reset during a race),
and no other lobby sound may use the reset sound. The pling marks the lobby's countdown and is not the race's countdown beat.

#### Scenario: The lobby pling is not the race beat
- **WHEN** the race countdown beat and the lobby pling are compared
- **THEN** they are different sounds, and the test named for this requirement fails if they become the same sample

#### Scenario: The full-lobby chime is the only shared sample
- **WHEN** each lobby sound is compared with the race sample list
- **THEN** only the full-lobby chime shares a sample, namely the reset chime, and the test named for this requirement fails if any other lobby sound does
