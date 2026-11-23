## ADDED Requirements

### Requirement: The Diamond medal and first place have their own sounds
**MoSCoW:** Must. THE SYSTEM SHALL play for the Diamond medal and for first place at the cup's end a sound that is neither the
countdown beat, nor the ring-pass chime, nor the reset chime, nor the DNF sound. The two sounds SHALL differ from each other.

#### Scenario: A Diamond finish does not sound like a ring pass
- **WHEN** a racer finishes a map with the Diamond medal
- **THEN** the medal sound is not the last-ring chime, and no ring-pass sound is played for that finish

#### Scenario: First place does not sound like a ring pass
- **WHEN** a racer is placed first at the cup's end
- **THEN** the placement sound is not the last-ring chime, and the Diamond sound and the first-place sound differ

#### Scenario: The samples exist in the 26.2 registry
- **WHEN** the sound samples for the Diamond medal and for first place are looked up in the 26.2 sound registry
- **THEN** each key is present, and a test fails if either key is missing
