# Spec Delta

## Purpose

A version 2 map has a key, so its default world follows the key. A version 1 map keeps the world rule it has always had.

## MODIFIED Requirements

### Requirement: A map's world defaults to its name
**Priority:** MoSCoW Must

WHEN a version 1 map file omits `world`, THE SYSTEM SHALL use the map's flat `name` exactly as written, with the same
characters and the same letter case, as the world directory name. WHEN a version 2 map file omits `world`, THE SYSTEM SHALL
use the value of its key, exactly as written, as the world directory name.

#### Scenario: World omitted
- **WHEN** a version 1 map file named `sky-drift` has no `world` field
- **THEN** the loaded map's world is `sky-drift`

#### Scenario: Derived world is not case-folded
- **WHEN** a version 1 map file named `elytraraceblueandred` has no `world` field
- **THEN** the loaded map's world is `elytraraceblueandred`, not `ElytraraceBlueAndRed`

#### Scenario: World omitted in version 2
- **WHEN** a version 2 map file with the key `elytrarace:sky_drift` has no `world` field
- **THEN** the loaded map's world is `sky_drift`
