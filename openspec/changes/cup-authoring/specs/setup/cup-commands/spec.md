# Spec Delta

## Purpose

A builder makes and edits a cup, the rotation of maps a race plays, from the setup server. A cup names only maps the game can
load, so the setup server refuses at save time a rotation the game would refuse at boot. Each command saves the cup before it
replies.

## ADDED Requirements

### Requirement: A builder creates a cup with its first map
**Priority:** MoSCoW Must

WHEN a builder runs `/cup new <id> <mode> <map>` with a valid cup id, a mode of `RACE` or `PRACTICE` in either letter case, a map that is
game-loadable, and no cup file named `<id>`, THE SYSTEM SHALL create the cup with that map as its only map, SHALL save it, and SHALL reply
with the cup's key. IF any of these does not hold, THEN THE SYSTEM SHALL create nothing and SHALL name the rule that failed.

#### Scenario: A new cup with one map
- **WHEN** a builder runs `/cup new alpha_run RACE blue_and_red` and `blue_and_red` is game-loadable
- **THEN** the cup `elytrarace:alpha_run` is saved with the mode `RACE` and the map list `elytrarace:blue_and_red`, and the reply shows the key

#### Scenario: The map is only a draft
- **WHEN** a builder runs `/cup new alpha_run RACE skyfortress` and `skyfortress` has a draft but no game-loadable map file
- **THEN** no cup file is written and the reply says the map `skyfortress` is not game-loadable and must have a spawn and a ring first

#### Scenario: The cup exists
- **WHEN** a builder runs `/cup new alpha_cup RACE blue_and_red` and a cup file `alpha_cup.json` exists
- **THEN** no file changes and the reply names the existing cup

### Requirement: A builder edits the cup's name and mode
**Priority:** MoSCoW Must

WHEN a builder runs `/cup name <id> <text>` or `/cup name <id> clear`, THE SYSTEM SHALL apply the name rules of `setup/map-metadata` (MiniMessage
text, the limits of 32 visible and 40 stored characters, and `clear` removing the name), SHALL save the cup, and SHALL reply. WHEN a builder runs
`/cup mode <id> <mode>`, THE SYSTEM SHALL set the mode, save the cup and reply. IF the cup does not exist, THEN THE SYSTEM SHALL name it and change nothing.

#### Scenario: Rename a cup
- **WHEN** a builder runs `/cup name alpha_run <green>Alpha Run`
- **THEN** the cup's display name is `<green>Alpha Run` and the saved file holds it

#### Scenario: Switch to practice
- **WHEN** a builder runs `/cup mode alpha_run PRACTICE` on a `RACE` cup
- **THEN** the cup's mode is `PRACTICE` and the saved file says so

#### Scenario: Unknown cup
- **WHEN** a builder runs `/cup mode nowhere RACE` and no cup file `nowhere.json` exists
- **THEN** no file is written and the reply names the cup `nowhere`

### Requirement: A builder adds a map to a cup at a position
**Priority:** MoSCoW Must

WHEN a builder runs `/cup map add <id> <map> [position]`, THE SYSTEM SHALL insert the map at the given 1-based position, or append it when no position is given, SHALL save the cup, and SHALL reply with the new map list.

#### Scenario: Append a map
- **WHEN** a cup of `blue_and_red` receives `/cup map add alpha_run palm_beach`
- **THEN** the map list is `blue_and_red`, `palm_beach`, and the file is saved

### Requirement: A builder removes a map from a cup by position
**Priority:** MoSCoW Must

WHEN a builder runs `/cup map remove <id> <position>`, THE SYSTEM SHALL remove the map at that 1-based position, SHALL move the maps after it up one position, SHALL save the cup, and SHALL reply with the new map list.

#### Scenario: Remove the middle map
- **WHEN** a cup of three maps receives `/cup map remove alpha_run 2`
- **THEN** the map list has two maps, the former third map is now second, and the file is saved

### Requirement: A builder moves a map of a cup to another position
**Priority:** MoSCoW Must

WHEN a builder runs `/cup map move <id> <from> <to>`, THE SYSTEM SHALL move the map at the 1-based position `from` to the position `to`, shift the maps between the two positions by one, SHALL save the cup, and SHALL reply with the new map list.

#### Scenario: Move a map to the front
- **WHEN** a cup of `a`, `b`, `c` receives `/cup map move alpha_run 3 1`
- **THEN** the map list is `c`, `a`, `b`

### Requirement: A position outside the map list is refused
**Priority:** MoSCoW Must

IF a position given to `/cup map add`, `/cup map remove` or `/cup map move` is outside the list, THEN THE SYSTEM SHALL change nothing and SHALL name the valid range.

#### Scenario: Position out of range
- **WHEN** a cup of two maps receives `/cup map remove alpha_run 3`
- **THEN** the file is not written and the reply names the range 1 to 2

### Requirement: A cup keeps at least one map
**Priority:** MoSCoW Must

IF a builder's command would leave a cup with no map, THEN THE SYSTEM SHALL refuse the command and change nothing, because the game refuses a cup with no map.

#### Scenario: Remove the only map
- **WHEN** a cup of one map receives `/cup map remove alpha_run 1`
- **THEN** the file is unchanged and the reply says a cup needs at least one map

### Requirement: Every map a cup names is game-loadable when the cup is saved
**Priority:** MoSCoW Must

WHEN a command would save a cup, THE SYSTEM SHALL check that every map the cup names is a map the game can load, using the catalogue's cross-check, and IF one is not, THEN THE SYSTEM SHALL refuse the save, SHALL change nothing, and SHALL name each map that is not game-loadable.

#### Scenario: A map that is not loadable is named
- **WHEN** a cup naming `blue_and_red` and `palm_beach` is saved and `palm_beach` has no file in the maps directory
- **THEN** the save is refused, the file is unchanged, and the reply names `palm_beach`

### Requirement: A builder sees what a cup plays and what is missing
**Priority:** MoSCoW Should

WHEN a builder runs `/cup info <id>`, THE SYSTEM SHALL reply with the cup's key, its mode, its display name if it has one, and each map with its 1-based position and whether the map is game-loadable.

#### Scenario: Info on a cup with a missing map
- **WHEN** a builder runs `/cup info alpha_run` and the cup names one map that is not game-loadable
- **THEN** the reply lists every map with its position and marks the missing one

### Requirement: Each cup command is gated by the setup permission and saves before it replies
**Priority:** MoSCoW Must

THE SYSTEM SHALL check `voyager.setup.use` for each `/cup` subcommand separately, and IF the sender does not hold it, THEN THE SYSTEM SHALL tell the sender the command is denied and change nothing. WHEN a command changes a cup, THE SYSTEM SHALL save the cup first and show the change only if the save succeeded; IF the save fails, THEN THE SYSTEM SHALL keep the previous file and tell the builder that the change was not saved.

#### Scenario: A sender without the node
- **WHEN** a player without `voyager.setup.use` runs `/cup map remove alpha_run 1`
- **THEN** the reply is the denied message and the file is unchanged

#### Scenario: Save fails on a map change
- **WHEN** a builder runs `/cup map add alpha_run palm_beach` and the write fails
- **THEN** the file holds its previous bytes and the builder sees the save-failed message

### Requirement: A cup edit is refused while another builder holds the cup
**Priority:** MoSCoW Should

WHILE another builder holds the claim on a cup id in the claim registry of `single-builder-map-claims`, THE SYSTEM SHALL refuse every edit of that cup by a different builder and SHALL name the holder. The claim is taken only by a command that opens a cup (open question 1 in `design.md`).

#### Scenario: A second builder edits a claimed cup
- **WHEN** builder A holds the claim on `alpha_run` and builder B runs `/cup name alpha_run Other`
- **THEN** the file is unchanged and the reply names builder A as the holder

### Requirement: The cup messages exist in English and German with the same placeholders
**Priority:** MoSCoW Must

THE SYSTEM SHALL provide every player-facing message of the `/cup` commands in `voyager_setup_en_US.properties` and in `voyager_setup_de_DE.properties`, each with the same `<arg:N>` placeholders in both bundles.

#### Scenario: Bundle parity
- **WHEN** the setup bundles are compared key by key
- **THEN** every cup message key exists in both bundles and the placeholder numbers of each key match
