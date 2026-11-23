## ADDED Requirements

### Requirement: A reset preloads its target through the non-blocking arrival path
**MoSCoW:** Must. WHEN a racer is reset within a map, THE SYSTEM SHALL preload the chunks around the reset target with the same
non-blocking preload that a map move uses, and SHALL NOT join the chunk futures or the teleport on the server tick.

#### Scenario: A reset preload is started on the detecting tick
- **WHEN** a racer is reset within a map on tick 300
- **THEN** the preload for the target is started on tick 300 and tick 300 returns without waiting for any chunk

#### Scenario: A failed reset preload holds the racer and is reported
- **WHEN** a chunk of a reset target fails to load
- **THEN** the racer stays held, the failure is logged once with the world name, and the tick is not interrupted
