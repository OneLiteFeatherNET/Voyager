# Spec Delta

## Purpose

A builder makes and edits a cup, the rotation of maps a race plays, from the setup server. A builder claims a cup before editing it,
so one builder edits a cup at a time. A cup names only maps the game can load, so the setup server refuses at save time a rotation
the game would refuse at boot. Each command saves the cup before it replies.

## ADDED Requirements

### Requirement: A builder creates a cup with its first map
**Priority:** MoSCoW Must

WHEN a builder runs `/cup new <id> <mode> <map>` with a valid cup id, a mode of `RACE` or `PRACTICE` in either letter case, a map that is
game-loadable, and no cup file named `<id>`, THE SYSTEM SHALL create the cup with that map as its only map, SHALL save it, and SHALL reply
with the cup's key. IF any of these does not hold, THEN THE SYSTEM SHALL create nothing and SHALL name the rule that failed.

#### Scenario: A new cup with one map
- **WHEN** a builder runs `/cup new alpha_run RACE blue_and_red` and `blue_and_red` is game-loadable
- **THEN** the cup `elytrarace:alpha_run` is saved with the mode `RACE` and the map list `elytrarace:blue_and_red`, the builder holds its claim, and the reply shows the key

#### Scenario: The map is only a draft
- **WHEN** a builder runs `/cup new alpha_run RACE skyfortress` and `skyfortress` has a draft but no game-loadable map file
- **THEN** no cup file is written, no claim is taken, and the reply says the map `skyfortress` is not game-loadable and must have a spawn and a ring first

#### Scenario: The cup exists
- **WHEN** a builder runs `/cup new alpha_cup RACE blue_and_red` and a cup file `alpha_cup.json` exists
- **THEN** no file changes and the reply names the existing cup

### Requirement: A builder opens an existing cup and sees its definition
**Priority:** MoSCoW Must

WHEN a builder runs `/cup open <id>` for an existing cup that no other builder holds, THE SYSTEM SHALL give the builder the claim on that cup
(see the claim requirement below) and SHALL reply with the cup's definition: its key, its mode, its display name if it has one, and each map
with its 1-based position and whether the game can load it. IF no cup file `<id>.json` exists, THEN THE SYSTEM SHALL name the cup and change
nothing.

#### Scenario: Open a cup with a missing map
- **WHEN** a builder runs `/cup open alpha_run` and the cup names one map that is not game-loadable
- **THEN** the builder holds the claim, the reply lists every map with its position, and the missing map is marked as not game-loadable

#### Scenario: Open an unknown cup
- **WHEN** a builder runs `/cup open nowhere` and no cup file `nowhere.json` exists
- **THEN** no claim is taken, no file is written, and the reply names the cup `nowhere`

### Requirement: A cup is claimed by the builder who opens it
**Priority:** MoSCoW Must

WHEN a builder runs `/cup new` for a cup it creates, or `/cup open <id>` for a cup that no other builder holds, THE SYSTEM SHALL give the builder
the claim on that cup under the key `cup:<id>` of the claim registry. IF another builder holds the claim, THEN THE SYSTEM SHALL refuse the open,
SHALL name the holder, and SHALL change nothing.

#### Scenario: A second builder is refused
- **WHEN** builder A holds `alpha_run` and builder B runs `/cup open alpha_run`
- **THEN** B is refused, the reply names builder A as the holder, and the file is unchanged

#### Scenario: A cup claim does not touch the open map
- **WHEN** builder A holds the map `skyfortress` and runs `/cup open alpha_run`, which is free
- **THEN** A holds both `skyfortress` and `alpha_run`

#### Scenario: Opening the same cup again changes nothing
- **WHEN** builder A holds `alpha_run` and runs `/cup open alpha_run` again
- **THEN** A still holds `alpha_run` and the file is unchanged

### Requirement: A builder holds at most one cup claim
**Priority:** MoSCoW Must

WHEN a builder opens another cup, THE SYSTEM SHALL take the claim on the new cup first and then release the claim on the one held before. A
builder's cup claim is also released when the builder disconnects. There is no `/cup close` command.

#### Scenario: Opening another cup releases the first
- **WHEN** builder A holds `alpha_run` and runs `/cup open beta_run`, which is free
- **THEN** A holds `beta_run`, and builder B can open `alpha_run`

#### Scenario: A disconnect releases the claim
- **WHEN** builder A holds `alpha_run` and disconnects
- **THEN** builder B can open `alpha_run`

#### Scenario: There is no close
- **WHEN** a builder runs `/cup close alpha_run`
- **THEN** the reply is the usage message for the `/cup` command, and no claim changes

### Requirement: Only the holder of a cup edits it
**Priority:** MoSCoW Must

WHEN a builder runs `/cup name`, `/cup mode`, `/cup map add`, `/cup map remove` or `/cup map move` for a cup, THE SYSTEM SHALL apply the change
only if the builder holds the cup's claim. IF the builder does not hold it, THEN THE SYSTEM SHALL change nothing and SHALL tell the builder to open
the cup first, or, when another builder holds the claim, SHALL name the holder.

#### Scenario: An edit without opening the cup
- **WHEN** a builder runs `/cup name alpha_run Alpha` without holding `alpha_run`
- **THEN** the file is unchanged and the reply tells the builder to open `alpha_run` first

#### Scenario: An edit of a cup another builder holds
- **WHEN** builder A holds `alpha_run` and builder B runs `/cup name alpha_run Other`
- **THEN** the file is unchanged and the reply names builder A as the holder

### Requirement: An administrator unlocks a claimed cup
**Priority:** MoSCoW Must

WHEN a sender with `voyager.setup.unlock` runs `/cup unlock <id>`, THE SYSTEM SHALL release the claim on that cup whichever builder holds it, and
SHALL change no file. The command is gated by `voyager.setup.unlock` alone. A sender without that node is refused, and the claim stays.

#### Scenario: An unlock frees a stuck claim
- **WHEN** builder A holds `alpha_run` and a sender with `voyager.setup.unlock` runs `/cup unlock alpha_run`
- **THEN** builder B can open `alpha_run`, and the file is unchanged

#### Scenario: A sender without the unlock node
- **WHEN** a sender without `voyager.setup.unlock` runs `/cup unlock alpha_run` while builder A holds it
- **THEN** the reply is the denied message, and builder A still holds the claim

### Requirement: A builder edits the cup's name and mode
**Priority:** MoSCoW Must

WHEN the holder of a cup runs `/cup name <id> <text>` or `/cup name <id> clear`, THE SYSTEM SHALL apply the name rules of `setup/map-metadata`
(MiniMessage text, at most 40 visible characters with the tags not counted, and `clear` removing the name), SHALL save the cup, and SHALL reply.
WHEN the holder runs `/cup mode <id> <mode>`, THE SYSTEM SHALL set the mode, save the cup and reply.

#### Scenario: Rename a cup
- **WHEN** the holder of `alpha_run` runs `/cup name alpha_run <green>Alpha Run`
- **THEN** the cup's display name is `<green>Alpha Run` and the saved file holds it

#### Scenario: Name too long
- **WHEN** the holder of `alpha_run` runs `/cup name alpha_run` with a text of 41 visible characters
- **THEN** the file is unchanged and the reply names the 40-character limit

#### Scenario: Switch to practice
- **WHEN** the holder of a `RACE` cup `alpha_run` runs `/cup mode alpha_run PRACTICE`
- **THEN** the cup's mode is `PRACTICE` and the saved file says so

### Requirement: A holder adds a map to a cup at a position
**Priority:** MoSCoW Must

WHEN the holder of a cup runs `/cup map add <id> <map> [position]`, THE SYSTEM SHALL insert the map at the given 1-based position, or append it when no
position is given, SHALL save the cup, and SHALL reply with the new map list.

#### Scenario: Append a map
- **WHEN** the holder of a cup of `blue_and_red` runs `/cup map add alpha_run palm_beach`
- **THEN** the map list is `blue_and_red`, `palm_beach`, and the file is saved

### Requirement: A holder removes a map from a cup by position
**Priority:** MoSCoW Must

WHEN the holder of a cup runs `/cup map remove <id> <position>`, THE SYSTEM SHALL remove the map at that 1-based position, SHALL move the maps after it up one
position, SHALL save the cup, and SHALL reply with the new map list.

#### Scenario: Remove the middle map
- **WHEN** the holder of a cup of three maps runs `/cup map remove alpha_run 2`
- **THEN** the map list has two maps, the former third map is now second, and the file is saved

### Requirement: A holder moves a map of a cup to another position
**Priority:** MoSCoW Must

WHEN the holder of a cup runs `/cup map move <id> <from> <to>`, THE SYSTEM SHALL move the map at the 1-based position `from` to the position `to`, shift the
maps between the two positions by one, SHALL save the cup, and SHALL reply with the new map list.

#### Scenario: Move a map to the front
- **WHEN** the holder of a cup of `a`, `b`, `c` runs `/cup map move alpha_run 3 1`
- **THEN** the map list is `c`, `a`, `b`

### Requirement: A position outside the map list is refused
**Priority:** MoSCoW Must

IF a position given to `/cup map add`, `/cup map remove` or `/cup map move` is outside the list, THEN THE SYSTEM SHALL change nothing and SHALL name the valid range.

#### Scenario: Position out of range
- **WHEN** the holder of a cup of two maps runs `/cup map remove alpha_run 3`
- **THEN** the file is not written and the reply names the range 1 to 2

### Requirement: A cup keeps at least one map
**Priority:** MoSCoW Must

IF a builder's command would leave a cup with no map, THEN THE SYSTEM SHALL refuse the command and change nothing, because the game refuses a cup with no map.

#### Scenario: Remove the only map
- **WHEN** the holder of a cup of one map runs `/cup map remove alpha_run 1`
- **THEN** the file is unchanged and the reply says a cup needs at least one map

### Requirement: Every map a cup names is game-loadable when the cup is saved
**Priority:** MoSCoW Must

WHEN a command would save a cup, THE SYSTEM SHALL check that every map the cup names is a map the game can load, using the catalogue's cross-check, and IF one is not,
THEN THE SYSTEM SHALL refuse the save, SHALL change nothing, and SHALL name each map that is not game-loadable.

#### Scenario: A map that is not loadable is named
- **WHEN** a cup naming `blue_and_red` and `palm_beach` is saved and `palm_beach` has no file in the maps directory
- **THEN** the save is refused, the file is unchanged, and the reply names `palm_beach`

### Requirement: Each cup command is gated by the setup permission and saves before it replies
**Priority:** MoSCoW Must

THE SYSTEM SHALL check `voyager.setup.use` for each `/cup` subcommand except `unlock` separately, and IF the sender does not hold it, THEN THE SYSTEM SHALL tell the
sender the command is denied and change nothing. WHEN a command changes a cup, THE SYSTEM SHALL save the cup first and show the change only if the save succeeded;
IF the save fails, THEN THE SYSTEM SHALL keep the previous file and tell the builder that the change was not saved.

#### Scenario: A sender without the node
- **WHEN** a player without `voyager.setup.use` runs `/cup map remove alpha_run 1`
- **THEN** the reply is the denied message and the file is unchanged

#### Scenario: Save fails on a map change
- **WHEN** the holder of `alpha_run` runs `/cup map add alpha_run palm_beach` and the write fails
- **THEN** the file holds its previous bytes and the builder sees the save-failed message

### Requirement: The cup messages exist in English and German with the same placeholders
**Priority:** MoSCoW Must

THE SYSTEM SHALL provide every player-facing message of the `/cup` commands in `voyager_setup_en_US.properties` and in `voyager_setup_de_DE.properties`, each with the same
`<arg:N>` placeholders in both bundles.

#### Scenario: Bundle parity
- **WHEN** the setup bundles are compared key by key
- **THEN** every cup message key exists in both bundles and the placeholder numbers of each key match
