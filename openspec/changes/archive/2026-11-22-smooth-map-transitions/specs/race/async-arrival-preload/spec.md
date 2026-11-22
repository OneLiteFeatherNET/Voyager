## ADDED Requirements

### Requirement: The chunk preload does not block the server tick
**MoSCoW:** Must. WHEN a racer is to be placed on a map, THE SYSTEM SHALL start loading the chunks around the spawn in the
background, and SHALL NOT wait for any of them on the server tick.

#### Scenario: Pending chunks
- **WHEN** the preload for a racer's placement has been started and no chunk is loaded yet
- **THEN** the tick that started it returns at once, and the other racers' ticks run as usual

#### Scenario: Large radius
- **WHEN** the preload covers 1,225 chunks at view distance 16
- **THEN** no tick waits for them, and the racer is placed on a tick after all are present

### Requirement: The racer is placed on the first tick after every chunk is present
**MoSCoW:** Must. WHEN every chunk around the spawn is present, THE SYSTEM SHALL place the racer on the next tick, and SHALL NOT
place the racer while any chunk is still loading.

#### Scenario: Last chunk arrives
- **WHEN** 1,224 of 1,225 chunks are present
- **THEN** the racer is not placed on that tick

#### Scenario: All chunks arrive
- **WHEN** the last chunk completes
- **THEN** the racer is placed on the next tick

### Requirement: A failed preload holds the racer and is reported once
**MoSCoW:** Must. IF a chunk around the spawn fails to load, THEN THE SYSTEM SHALL keep the racer held in place, SHALL log the
failure once with the world name, and SHALL retry the placement at the next wait. THE SYSTEM SHALL NOT throw the failure into the
server tick.

#### Scenario: One chunk fails
- **WHEN** one chunk's load completes exceptionally
- **THEN** the racer stays held in place, the failure is logged once with the world name, and the tick is not interrupted

### Requirement: Each racer's preload is independent
**MoSCoW:** Must. THE SYSTEM SHALL track each racer's preload separately, so that one racer's pending chunks do not delay another
racer's placement that has all its chunks present.

#### Scenario: Two racers
- **WHEN** racer A's chunks are still loading and racer B's are all present
- **THEN** racer B is placed on the next tick, and racer A is not
