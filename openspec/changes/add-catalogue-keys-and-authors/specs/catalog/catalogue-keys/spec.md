# Spec Delta

## Purpose

A map and a cup are identified by an Adventure `Key` in the namespace `elytrarace`, for example `elytrarace:blue_and_red`.
The key is the catalogue's identifier, the reference between a cup and its maps, the operator's handle and the statistics
key. A file written before this change, with a flat id, keeps loading, and its id is read as a key.

## ADDED Requirements

### Requirement: A map and a cup are identified by a key in the elytrarace namespace
**MoSCoW:** Must. WHEN a map or cup file states its identifier, THE SYSTEM SHALL read it as an Adventure key whose namespace
is `elytrarace` and whose value matches `[a-z0-9][a-z0-9_-]{0,31}`. IF the namespace is another one, or the value does not
match, THEN THE SYSTEM SHALL refuse the file and the message SHALL name the file and the field.

#### Scenario: A key in the elytrarace namespace loads
- **WHEN** a version 2 map file states `"key": "elytrarace:blue_and_red"`
- **THEN** the map's key is `elytrarace:blue_and_red`

#### Scenario: A key in another namespace is refused
- **WHEN** a cup file states `"key": "other:alpha_cup"`
- **THEN** the file is refused and the message names the file and the field `key`

#### Scenario: A value with capitals is refused
- **WHEN** a map file states `"key": "elytrarace:BlueAndRed"`
- **THEN** the file is refused and the message names the file and the field `key`

### Requirement: A version 1 file's flat id is read as a key in the elytrarace namespace
**MoSCoW:** Must. WHEN a version 1 map or cup file (no `schemaVersion`, or `1`) states a flat `name` whose value matches
`[a-z0-9][a-z0-9_-]{0,31}`, THE SYSTEM SHALL read the identifier as `elytrarace:<name>`, and a cup's `mapNames` entries
SHALL be read the same way. IF the flat name does not match, THEN THE SYSTEM SHALL refuse the file and name the file and
the field.

#### Scenario: The shipped flat id keeps loading
- **WHEN** a version 1 map file states `"name": "palmbeach"`
- **THEN** the map's key is `elytrarace:palmbeach` and the file is not refused

#### Scenario: A cup's flat map reference resolves to a key
- **WHEN** a version 1 cup file lists `"elytraraceblueandred"` in `mapNames`
- **THEN** the cup's map is `elytrarace:elytraraceblueandred`, which resolves to the map whose key is that value

#### Scenario: A flat name with capitals is refused
- **WHEN** a version 1 map file states `"name": "ElytraraceBlueAndRed"`
- **THEN** the file is refused and the message names the file and the field `name`

### Requirement: A version 2 map or cup states its key and not a name
**MoSCoW:** Must. WHEN a file states `schemaVersion` 2, THE SYSTEM SHALL read its identifier from the `key` field, and
SHALL refuse the file if `key` is absent. A version 2 file that states `name` SHALL be refused, because the two fields
would disagree.

#### Scenario: Version 2 without key
- **WHEN** a version 2 map file has no `key` field
- **THEN** the file is refused and the message names the file and `key`

#### Scenario: Version 2 that also states name
- **WHEN** a version 2 cup file states both `key` and `name`
- **THEN** the file is refused and the message names the file and the field `name`

### Requirement: A version 2 map without a world takes the value of its key
**MoSCoW:** Must. WHEN a version 2 map file omits `world`, THE SYSTEM SHALL use the value of its key, as written, as its
world directory name. A version 1 map keeps the rule that its world is its name.

#### Scenario: Version 2 world omitted
- **WHEN** a version 2 map file with the key `elytrarace:sky_drift` has no `world`
- **THEN** the map's world is `sky_drift`

### Requirement: Operator inputs accept the flat id and the key
**MoSCoW:** Must. THE SYSTEM SHALL accept, for the cup setting `VOYAGER_CUP` and for every operator or statistics argument
that names a cup or a map, either the key or a flat id, and SHALL read a flat id as `elytrarace:<id>`. The setting SHALL be
read through the configuration loader.

#### Scenario: The flat cup setting
- **WHEN** `VOYAGER_CUP` is `alpha_cup`
- **THEN** the server plays the cup `elytrarace:alpha_cup`

#### Scenario: The key as a cup setting
- **WHEN** `VOYAGER_CUP` is `elytrarace:alpha_cup`
- **THEN** the server plays the same cup

#### Scenario: A map argument in the flat form
- **WHEN** an operator runs a statistics command with the map `blue_and_red`
- **THEN** the command answers for the map `elytrarace:blue_and_red`

### Requirement: Operator output prints the key
**MoSCoW:** Must. THE SYSTEM SHALL print the key of a cup or map in operator output, console output and log lines, in the form
`elytrarace:<value>`, so an operator can copy it into a command.

#### Scenario: The reload report names the key
- **WHEN** a catalogue reload finds a problem in `elytrarace:alpha_cup`
- **THEN** the report names `elytrarace:alpha_cup`

### Requirement: A cup's maps resolve by key across the catalogue
**MoSCoW:** Must. THE SYSTEM SHALL resolve each entry of a cup's `mapNames` to a map whose key is equal, and SHALL report a
cup that names a key no map has, with the cup's key and the missing key named.

#### Scenario: A dangling reference is named
- **WHEN** `elytrarace:alpha_cup` names `elytrarace:missing_map`
- **THEN** the catalogue reports a problem naming both keys
