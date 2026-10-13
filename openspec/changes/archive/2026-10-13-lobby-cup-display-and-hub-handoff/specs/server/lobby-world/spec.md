# Spec Delta

## Purpose

The optional hologram position of the lobby world, and its configuration check. The lobby world itself is `add-lobby-world`'s.

## ADDED Requirements

### Requirement: The hologram position is an optional lobby setting
**MoSCoW:** Should. THE SYSTEM SHALL read `VOYAGER_LOBBY_HOLOGRAM` as three numbers `x,y,z` in block coordinates, parsed as
`VOYAGER_LOBBY_SPAWN` is. THE SYSTEM SHALL treat the setting as optional, with no default.

#### Scenario: Position read
- **WHEN** `VOYAGER_LOBBY_HOLOGRAM` is `0,70,0`
- **THEN** the lobby's hologram position is 0, 70, 0

#### Scenario: Not set
- **WHEN** `VOYAGER_LOBBY_HOLOGRAM` is not set
- **THEN** the server has no hologram position

### Requirement: A hologram position needs a lobby world
**MoSCoW:** Must. IF `VOYAGER_LOBBY_HOLOGRAM` is set and `VOYAGER_LOBBY_WORLD` is not, THEN THE SYSTEM SHALL refuse to start and
the configuration check SHALL name both settings.

#### Scenario: Hologram without a lobby
- **WHEN** `VOYAGER_LOBBY_HOLOGRAM=0,70,0` is set and `VOYAGER_LOBBY_WORLD` is not
- **THEN** the configuration check reports an error that names `VOYAGER_LOBBY_HOLOGRAM` and `VOYAGER_LOBBY_WORLD`

### Requirement: A malformed hologram position is refused
**MoSCoW:** Must. IF `VOYAGER_LOBBY_HOLOGRAM` is not three numbers, THEN THE SYSTEM SHALL refuse to start and name the setting.

#### Scenario: Two numbers
- **WHEN** `VOYAGER_LOBBY_HOLOGRAM=0,70` is set
- **THEN** the configuration check reports an error that names `VOYAGER_LOBBY_HOLOGRAM` and says it must be three numbers

### Requirement: The configuration check lists the hologram setting
**MoSCoW:** Must. THE SYSTEM SHALL name `VOYAGER_LOBBY_HOLOGRAM` in the configuration check's report, keyed by setting name, as
the other lobby settings are (`add-lobby-world`, "Every lobby setting is named in the configuration check").

#### Scenario: Unset hologram is not a problem
- **WHEN** the configuration check runs with a lobby and no hologram position
- **THEN** the report has no problem for the hologram
