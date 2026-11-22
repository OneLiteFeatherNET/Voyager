# Spec Delta

## Purpose

A map and a cup record who built it. An author is a Minecraft player's UUID and the name that player is shown under. The
catalogue stores both, so the game can name the builders without a network lookup at load.

## ADDED Requirements

### Requirement: A map or cup may state its authors as UUID and name
**MoSCoW:** Must. WHEN a map or cup file states `authors`, THE SYSTEM SHALL read it as an array of objects, each with a
`uuid` and a `name`. The `authors` field is optional, and a file without it has no authors.

#### Scenario: A map with two authors
- **WHEN** a map file states two authors, each with a valid `uuid` and a `name`
- **THEN** the map has those two authors in the order the file gives them

#### Scenario: A file without authors
- **WHEN** a cup file has no `authors` field
- **THEN** the cup has no authors and is not refused for that reason

### Requirement: An author's UUID is a UUID and is compared by value
**MoSCoW:** Must. WHEN an author's `uuid` is a valid UUID in the canonical 8-4-4-4-12 hexadecimal form, in either letter case, THE
SYSTEM SHALL read it as that UUID and write it in lower case. IF it is not a valid UUID, or two authors of one map or cup have
the same UUID, THEN THE SYSTEM SHALL refuse the file and name the file and the field.

#### Scenario: A UUID in capitals is normalised
- **WHEN** an author's `uuid` is `"0F3B2C1D-AAAA-4BBB-8CCC-1234567890AB"`
- **THEN** the author's UUID is `0f3b2c1d-aaaa-4bbb-8ccc-1234567890ab`

#### Scenario: A duplicate author is refused
- **WHEN** a map file lists the same UUID twice
- **THEN** the file is refused and the message names the file and the field `authors`

### Requirement: An author's name is a Minecraft username shape and is shown as written
**MoSCoW:** Must. WHEN an author's `name` matches `[A-Za-z0-9_]{1,16}`, THE SYSTEM SHALL keep it exactly as written. IF it does
not match, THEN THE SYSTEM SHALL refuse the file and name the file and the field. THE SYSTEM SHALL NOT look the name up at load.

#### Scenario: A name that is too long is refused
- **WHEN** an author's `name` is seventeen characters long
- **THEN** the file is refused and the message names the file and the field `name`

#### Scenario: A name is not refreshed
- **WHEN** a player renamed in Mojang's service after the file was written
- **THEN** the catalogue keeps the name the file states

### Requirement: A map or cup has at most eight authors
**MoSCoW:** Should. IF a map or cup states more than eight authors, THEN THE SYSTEM SHALL refuse the file and name the file and
the field `authors`.

#### Scenario: Nine authors
- **WHEN** a cup lists nine authors
- **THEN** the cup file is refused and the message names the file and `authors`

### Requirement: The shipped maps name their authors as approved by the owner
**MoSCoW:** Must. THE SYSTEM SHALL ship `elytrarace:blue_and_red` with the author TheMeinerLP, and `elytrarace:palm_beach`,
`elytrarace:dragon_lobby` and `elytrarace:memoria` with the author Seelenretterin, using the UUIDs of the migration table in
`proposal.md`. The shipped cups have no authors.

#### Scenario: The shipped map with its author
- **WHEN** the shipped catalogue is loaded
- **THEN** the map `elytrarace:blue_and_red` has the author TheMeinerLP with UUID `05bf52c6-7bb0-4f13-8951-0e1fd803df35`

#### Scenario: The three legacy maps share their author
- **WHEN** the shipped catalogue is loaded
- **THEN** `elytrarace:palm_beach`, `elytrarace:dragon_lobby` and `elytrarace:memoria` each have the author Seelenretterin with UUID `e1dff87a-1059-47f1-bc7d-533f072bf8c7`
