# Spec Delta

## Purpose

The draft file is written in version 3 when one of its rings is an oval, and in version 2 otherwise, so that a draft of
circles stays readable by every server that reads version 2. The oval fields follow the radius of the ring they belong to.

## MODIFIED Requirements

### Requirement: The draft JSON uses the game server's map format
**Priority:** MoSCoW Must

THE SYSTEM SHALL write the draft with `schemaVersion` as `2` first, or as `3` when one of its rings is an oval, then the keys
`key`, `world`, `displayName` (only when set), `authors` (only when set), `spawn`, `referenceTimeSeconds`, `boostConfig`,
`guideLine` and `rings`, and each ring with the keys `index`, `center`, `normal`, `radius`, `points` and `type`, followed, for an
oval ring only, by `radiusMinor` and `majorAxis`, using the same value shapes as the shipped map. The `key` SHALL be
`elytrarace:` followed by the map's folder name. `index` SHALL always be written. WHILE no spawn is set, THE SYSTEM SHALL omit
the `spawn` key.

#### Scenario: Draft with one ring and a spawn
- **WHEN** a draft with a spawn and one ring is saved
- **THEN** the file has every key listed above, `schemaVersion` is 2, `key` is `elytrarace:<folder name>`, the ring's `index` is 0 and its `type` is the string `STANDARD`

#### Scenario: Draft without spawn
- **WHEN** a draft without spawn is saved
- **THEN** the file has no `spawn` key

#### Scenario: A save keeps the display name and the authors
- **WHEN** a map that has `displayName` "Blue & Red" and two authors is loaded into the setup server and saved without edits
- **THEN** the saved file has the same `displayName` and the same two authors

#### Scenario: A map without display name or authors writes neither key
- **WHEN** a map with no `displayName` and no `authors` is saved
- **THEN** the file has no `displayName` and no `authors` key

#### Scenario: Draft with an oval ring
- **WHEN** a draft with a spawn and one oval ring of `radius` 6 and `radiusMinor` 3 is saved
- **THEN** the file's `schemaVersion` is 3, the ring has `radius` 6, `radiusMinor` 3 and the `majorAxis` of the oval, and the file loads again as the same oval

#### Scenario: Draft of circles stays at version 2
- **WHEN** a draft whose rings are all circles is saved
- **THEN** the file's `schemaVersion` is 2 and the ring objects have no `radiusMinor` or `majorAxis` key
