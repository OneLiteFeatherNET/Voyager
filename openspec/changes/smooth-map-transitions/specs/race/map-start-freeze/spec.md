## ADDED Requirements

### Requirement: Every racer waits frozen at the spawn, facing the first ring, until GO
**MoSCoW:** Must. WHEN a racer is placed on a map, THE SYSTEM SHALL hold the racer at the map's spawn, facing the map's first
ring, without gliding, until the GO tick. This applies to every map of a cup, not only the first.

#### Scenario: Second map of a cup
- **WHEN** a cup moves from its first map to its second, and the racer is placed on the second map before its countdown
- **THEN** the racer is held at the second map's spawn, facing the second map's first ring, until GO

#### Scenario: A racer who moves during the count
- **WHEN** a racer walks more than 0.1 block from the spawn during the count
- **THEN** the racer is returned to the spawn and their velocity is set to zero, on the same tick

### Requirement: A racer who deploys the elytra before GO is held on the ground
**MoSCoW:** Must. WHILE a racer is frozen before GO, THE SYSTEM SHALL turn their gliding off on each tick, so that a jump
and deploy does not lift the racer off the spawn.

#### Scenario: Jump and deploy during the count
- **WHEN** a frozen racer jumps and deploys the elytra before GO
- **THEN** the racer is returned to the spawn and is not gliding

### Requirement: The freeze holds while the chunks around the spawn load
**MoSCoW:** Must. WHILE the chunks around a map's spawn are loading before a racer is placed, THE SYSTEM SHALL keep the racer
where they stand and SHALL NOT place the racer on the map until the chunks are present. IF the chunks fail to load, THEN THE
SYSTEM SHALL leave the racer where they stand, as `Arrivals` does.

#### Scenario: A wide preload
- **WHEN** the chunks within the view distance of the spawn take several seconds to load
- **THEN** the racer is not moved until they are present, and is then placed at the spawn and frozen

### Requirement: GO releases a frozen racer with no velocity and no gliding
**MoSCoW:** Must. ON the GO tick, THE SYSTEM SHALL release each frozen racer to fly on their own. THE SYSTEM SHALL give no
velocity and SHALL NOT set the racer gliding, as `waiting-lobby` requires.

#### Scenario: GO
- **WHEN** the GO tick arrives for a frozen racer
- **THEN** the freeze ends, the velocity is not set, and the racer keeps the elytra they had in hand
