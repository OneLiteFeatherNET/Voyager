# platform/world-lighting Specification

## Purpose
TBD - created by archiving change world-lighting. Update Purpose after archive.

## Requirements

### Requirement: Every opened world uses the lighting chunk
**Priority:** MoSCoW Must

WHEN `MapInstances` opens a world, THE SYSTEM SHALL build its instance with Minestom's `LightingChunk` as the chunk
supplier, so that the map worlds and the lobby world are lit by the same engine.

#### Scenario: A map world is opened
- **WHEN** the server opens a map's world through `MapInstances.forWorld`
- **THEN** every chunk the instance creates is a `LightingChunk`

#### Scenario: The lobby world is opened
- **WHEN** `LobbyTransition` opens the lobby world through `MapInstances`
- **THEN** its chunks are lit by the same engine as a map's chunks, with no separate code path

### Requirement: Stored light is kept as it was saved
**Priority:** MoSCoW Must

WHEN a chunk loads and a section carries a stored `SkyLight` or `BlockLight` array, THE SYSTEM SHALL keep that array
and SHALL NOT recompute it.

#### Scenario: A chunk with stored light is loaded
- **WHEN** a chunk whose sections carry stored sky and block light is loaded
- **THEN** every such section still reports that its light does not need an update, and its stored arrays are unchanged

### Requirement: A section without stored light is computed on first view
**Priority:** MoSCoW Must

WHEN a chunk loads and a section carries no stored array for a light kind, THE SYSTEM SHALL mark that light for relight,
and SHALL compute it the first time a viewer is sent that chunk.

#### Scenario: A light-less section is sent to a viewer
- **WHEN** a viewer is sent a chunk with a section that has no stored block light
- **THEN** the light packet for that section carries a computed block light array, not an empty one

#### Scenario: A section with an all-zero stored array is treated as stored
- **WHEN** a section carries a stored light array of 2048 bytes that are all zero
- **THEN** that array is kept as stored and is not marked for relight

### Requirement: The boot configuration check computes no light
**Priority:** MoSCoW Must

WHEN the configuration check reads every chunk of a world through `MapInstances.checkWorld` or `readEveryChunk`, THE
SYSTEM SHALL NOT compute light for any chunk, so that the check's time and peak memory do not include lighting.

#### Scenario: A world with a light-less section is checked
- **WHEN** `checkWorld` reads a world that has a section with no stored block light
- **THEN** after the read that section still reports that its light needs an update, which shows no relight ran

### Requirement: Light is never written to disk
**Priority:** MoSCoW Must

THE SYSTEM SHALL NOT write any light array to a region file. A relit chunk SHALL stay in memory only, and the region
files of a world SHALL be byte-identical before and after a viewer is sent its relit chunks.

#### Scenario: A relit chunk is sent and the server stops
- **WHEN** a viewer is sent a relit chunk and `MapInstances.close()` runs
- **THEN** the region file of that chunk is byte-identical to its state before the server started

### Requirement: The change adds no lighting dependency
**Priority:** MoSCoW Should

THE SYSTEM SHALL use the lighting engine that Minestom ships and SHALL NOT add `falco-light` or `falco-instance` to
`voyager-platform`, so that the light engine has no experimental API and no second chunk type.

#### Scenario: The platform module is built
- **WHEN** `voyager-platform` resolves its dependencies
- **THEN** neither `falco-light` nor `falco-instance` is on its compile or runtime classpath

### Requirement: No dark area on a viewed chunk
**Priority:** MoSCoW Should

WHILE a viewer is inside a world, THE SYSTEM SHALL send every chunk in the viewer's range with light that is not empty
for each section that has any light, so that the viewer does not see a dark area in a loaded world.

#### Scenario: A viewer walks into an unlit area
- **WHEN** a viewer enters chunks that carry no stored light
- **THEN** each chunk sent to the viewer carries computed light, and the first send of a 289-chunk view (view distance 8) stays within the
  time budget measured in task 6.2

### Requirement: Relight cost is measured before the change is accepted
**Priority:** MoSCoW Should

THE SYSTEM SHALL record the boot time, the boot memory and the time to the first light packet of a 289-chunk view for
the ElytraLobby world, with and without this change, before the pull request is merged.

#### Scenario: The change is measured on the lobby world
- **WHEN** the smoke boot runs with the lobby world configured
- **THEN** the pull request records the three numbers, with and without the change

### Requirement: Lighting is not a gameplay change
**Priority:** MoSCoW Must

THE SYSTEM SHALL NOT change the race, cup, scoring, ring or golden-transcript behaviour through lighting, so that
`CupSessionGoldenMasterTest` stays byte-identical.

#### Scenario: The golden transcripts are replayed
- **WHEN** the golden-master tests run after this change
- **THEN** every transcript is byte-identical to its committed copy
