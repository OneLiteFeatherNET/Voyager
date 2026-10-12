# server/lobby-world Specification

## Purpose

Defines how the server names its lobby world and that world's spawn, what happens when neither is configured, how the
lobby world is loaded and checked at boot, and the maximum racer setting. It is the operator-facing contract of the
waiting room's world, and it is what a deployment sets in its environment.

## Requirements

### Requirement: The lobby world is named by two optional settings that go together
**MoSCoW:** Must. THE SYSTEM SHALL read the lobby world from the setting `VOYAGER_LOBBY_WORLD`, a world directory name,
and the lobby spawn from `VOYAGER_LOBBY_SPAWN`, a block position `x,y,z`. THE SYSTEM SHALL refuse to start when one of the
two is set and the other is not, and the refusal SHALL name the setting that is missing.

#### Scenario: Both settings given
- **WHEN** the server starts with `VOYAGER_LOBBY_WORLD=ElytraLobby` and `VOYAGER_LOBBY_SPAWN=0,64,0`
- **THEN** the lobby world is `ElytraLobby` with its spawn at (0, 64, 0)

#### Scenario: Only the world is given
- **WHEN** the server starts with `VOYAGER_LOBBY_WORLD=ElytraLobby` and no spawn
- **THEN** the server refuses to start, and the message names `VOYAGER_LOBBY_SPAWN`

#### Scenario: A spawn that is not three numbers
- **WHEN** the server starts with `VOYAGER_LOBBY_SPAWN=0,64`
- **THEN** the server refuses to start, and the message names the setting and the text it read

### Requirement: Without a lobby world the waiting room keeps the first map's spawn and says so
**MoSCoW:** Must. WHEN neither lobby setting is set, THE SYSTEM SHALL place waiting racers at the first map's spawn of the
current catalogue, as before this change. THE SYSTEM SHALL log one warning at boot that names both settings, and the
configuration check SHALL report the same as a warning, not an error.

#### Scenario: No lobby configured
- **WHEN** the server starts with neither lobby setting
- **THEN** the server starts, a warning names `VOYAGER_LOBBY_WORLD` and `VOYAGER_LOBBY_SPAWN`, and a joining racer stands at the first map's spawn

#### Scenario: The configuration check does not fail on the missing lobby
- **WHEN** the configuration check runs with neither lobby setting
- **THEN** its result lists one warning about the lobby and exits with success

### Requirement: The lobby world is loaded at boot and held for the life of the server
**MoSCoW:** Must. WHEN a lobby world is configured, THE SYSTEM SHALL load it at boot, after the cup's worlds, and SHALL
hold it open until the server stops. THE SYSTEM SHALL NOT save the lobby world's chunks.

#### Scenario: A missing lobby folder stops the boot
- **WHEN** `VOYAGER_LOBBY_WORLD` names a folder that is not in the worlds directory
- **THEN** the server refuses to start before a player can join, and the message names the folder

#### Scenario: The lobby world is not written
- **WHEN** a session runs with the lobby world loaded and the server stops
- **THEN** no file in the lobby world's folder has changed

### Requirement: The lobby world is checked at boot with one world open at a time
**MoSCoW:** Must. WHEN the boot or the configuration check runs with a lobby world configured, THE SYSTEM SHALL check the
lobby world's folder, its region data and its health, reporting a problem as an error. THE SYSTEM SHALL close each world
before the next one is loaded, so that at most one world is open during the check.

#### Scenario: A lobby world with unreadable region data is an error
- **WHEN** the lobby world's region file cannot be read
- **THEN** the configuration check reports an error that names the world's folder and the failed counters

#### Scenario: Two worlds are checked in turn
- **WHEN** the check reads the health of the lobby world and of a cup map's world
- **THEN** the first world is closed before the second is loaded

### Requirement: The lobby world is not a catalogue map's world
**MoSCoW:** Must. IF `VOYAGER_LOBBY_WORLD` names the world of any map in the catalogue, THEN THE SYSTEM SHALL refuse the
configuration with an error that names both the setting and the map, because a racer waiting in a map's instance would be
inside that map's race.

#### Scenario: The lobby names a map's world
- **WHEN** `VOYAGER_LOBBY_WORLD` names the world that a catalogue map plays
- **THEN** the configuration check reports an error naming `VOYAGER_LOBBY_WORLD` and that map

### Requirement: The maximum racer count is a setting that defaults to sixteen
**MoSCoW:** Must. THE SYSTEM SHALL read the maximum racer count from `VOYAGER_MAX_PLAYERS`, which defaults to 16 in
production and in dev mode. THE SYSTEM SHALL refuse a value below the minimum racer count, and the refusal SHALL name both
settings.

#### Scenario: Default maximum
- **WHEN** the server starts without `VOYAGER_MAX_PLAYERS`
- **THEN** the maximum racer count is 16

#### Scenario: A maximum below the minimum
- **WHEN** the server starts with `VOYAGER_MIN_PLAYERS=3` and `VOYAGER_MAX_PLAYERS=2`
- **THEN** the server refuses to start, and the message names both settings

### Requirement: Every lobby setting is named in the configuration check
**MoSCoW:** Must. THE SYSTEM SHALL report each problem with the lobby settings, and the maximum racer count, under the key
of the setting that caused it, as the configuration check does for every other setting.

#### Scenario: A problem names its setting
- **WHEN** the configuration check finds that `VOYAGER_LOBBY_SPAWN` cannot be read
- **THEN** the problem's key is `VOYAGER_LOBBY_SPAWN` and its source is the setting name
