# Spec Delta

## Purpose

A cup and a map have a name that players read. The catalogue gives each one an optional display name, shows it in place
of the id on every player-facing surface, and never translates it, because a display name is a proper noun.

## ADDED Requirements

### Requirement: A cup or map has an optional display name that falls back to its id
**MoSCoW:** Must. THE SYSTEM SHALL read an optional `displayName` string from each cup file and each map file. WHEN the field
is absent, blank or missing, THE SYSTEM SHALL use the id of the cup or map as its display name.

#### Scenario: A map with a display name
- **WHEN** the map file `elytraraceblueandred.json` names `displayName` "Blue & Red"
- **THEN** the map's display name is "Blue & Red"

#### Scenario: A map without a display name
- **WHEN** a map file has no `displayName` field
- **THEN** the map's display name is its id

#### Scenario: A blank display name falls back to the id
- **WHEN** a cup file sets `displayName` to an empty string or only spaces
- **THEN** the cup's display name is its id, and the loader logs no error for it

### Requirement: A display name is plain text and is refused when it is unusable
**MoSCoW:** Must. THE SYSTEM SHALL show a display name as the literal text written in the file, without reading any MiniMessage
tag in it. IF a display name is longer than 40 characters, THEN THE SYSTEM SHALL refuse the file with an error that names the
field and the file.

#### Scenario: A name with angle brackets is shown literally
- **WHEN** a map's display name is "<rainbow>Blue</rainbow>"
- **THEN** every player-facing line shows the text "<rainbow>Blue</rainbow>" and no colour change

#### Scenario: A name longer than forty characters is refused
- **WHEN** a cup file sets a display name of 41 characters
- **THEN** the loader refuses the file and the error names `displayName` and the file

### Requirement: The display name replaces the id on every player-facing surface
**MoSCoW:** Must. THE SYSTEM SHALL show the display name of a cup or map, not its id, on the boss bar, the map banner in chat,
the cup heading in chat, the map and cup results, the lobby sidebar and the lobby hologram. Any requirement in another
specification that names a cup name or a map name for a player means this display name.

#### Scenario: The map banner names the map
- **WHEN** a map of the cup "alpha_cup" starts
- **THEN** the chat banner names the map "Blue & Red" and not "elytraraceblueandred"

#### Scenario: The lobby names the cup
- **WHEN** a racer waits for the cup "alpha_cup"
- **THEN** the lobby boss bar and the lobby sidebar name the cup "Alpha Cup"

#### Scenario: The results name the map
- **WHEN** a map ends and the results are sent
- **THEN** the map result names the map by its display name

### Requirement: A display name is never translated
**MoSCoW:** Must. THE SYSTEM SHALL show the same display name to every player, in every locale, and SHALL NOT look up a
translation key for a cup or map name.

#### Scenario: A German player sees the English name
- **WHEN** a player whose client locale is `de_DE` waits for the cup "legacy_cup"
- **THEN** the player sees "Testing Cup", the same text an `en_US` player sees

### Requirement: Operator and log output keeps the id
**MoSCoW:** Should. THE SYSTEM SHALL keep the id of a cup or map in command output, in the catalogue validation report and in
log lines, so an operator can find the file that a name belongs to.

#### Scenario: The validation report names the file
- **WHEN** the catalogue validation check runs over the shipped catalogue
- **THEN** each cup and map line names its id, as before this change

### Requirement: The shipped catalogue names its cups and maps
**MoSCoW:** Must. THE SYSTEM SHALL ship display names for the shipped catalogue as follows: cup `alpha_cup` "Alpha Cup"; map
`elytraraceblueandred` "Blue & Red"; cup `legacy_cup` "Testing Cup"; maps `palmbeach` "PalmBeach", `dragonlobby` "DragonLobby"
and `memoria` "Memoria". The names are taken from the notes already in those files, with the MiniMessage gradient and rainbow
tags removed.

#### Scenario: The shipped names load
- **WHEN** the shipped catalogue is loaded
- **THEN** the display names are "Alpha Cup", "Blue & Red", "Testing Cup", "PalmBeach", "DragonLobby" and "Memoria"
