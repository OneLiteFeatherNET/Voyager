# Spec Delta

## Purpose

A builder names a map, credits the players who built it, and sets how long its reference lap is measured against, from the
setup server, without editing the file. The commands save the draft the way every other `/map` command does, and they keep
everything else in the draft as it was.

## ADDED Requirements

### Requirement: A builder sets the display name with MiniMessage text
**Priority:** MoSCoW Must

WHEN a builder runs `/map name <text>` in an open map and the text parses as MiniMessage, THE SYSTEM SHALL store the text as
written as the draft's display name, SHALL save the draft, and SHALL reply with the saved name. IF the text does not parse,
THEN THE SYSTEM SHALL leave the draft unchanged and SHALL reply with the position of the problem.

#### Scenario: A coloured name is saved
- **WHEN** a builder with `elytrarace:blue_and_red` open runs `/map name <blue>Blue <red>& Red`
- **THEN** the draft's display name is `<blue>Blue <red>& Red`, the draft file is saved, and the reply shows the saved name

#### Scenario: An unknown tag is refused
- **WHEN** a builder runs `/map name <sparkle>Blue`
- **THEN** no name is stored, the file is not written, and the reply names the tag `sparkle` and its position

#### Scenario: A tag outside the name tag set is refused
- **WHEN** a builder runs `/map name <click:run_command:/stop>Blue</click>`, a tag MiniMessage parses but the name rule does not allow
- **THEN** no name is stored, the file is not written, and the reply names the tag `click`

### Requirement: A display name is at most forty visible characters
**Priority:** MoSCoW Must

THE SYSTEM SHALL measure a display name's length in the characters a player sees: the text after `MiniMessage.stripTags`, counted in code points, with the MiniMessage
tags not counted. IF the visible text has more than 40 characters, THEN THE SYSTEM SHALL refuse the name, leave the draft unchanged, and name the 40-character limit.
This is the rule of `catalog/display-names` in `show-colored-map-and-cup-names`, which the catalogue reader applies to the same text.

#### Scenario: Tags do not count
- **WHEN** a builder runs `/map name <gradient:#00c2ff:#5bff8f>Blue and Red Rush</gradient>`
- **THEN** the visible length is 17, the name is accepted, and the draft's display name is the text as written

#### Scenario: Forty-one visible characters are refused
- **WHEN** a builder runs `/map name` with a text that shows 41 characters after its tags are removed
- **THEN** the draft is unchanged and the reply names the 40-character limit

### Requirement: A name is removed by clear or by a blank text
**Priority:** MoSCoW Must

WHEN a builder runs `/map name clear`, or a name whose text has no visible characters, THE SYSTEM SHALL remove the draft's display name
and SHALL save the draft. The word `clear` written alone is the removal; a name that is the word itself is written with a tag, for
example `<white>clear`, and is stored as a name.

#### Scenario: Clear removes the name
- **WHEN** a map with display name `Blue & Red` runs `/map name clear`
- **THEN** the draft has no display name, and the saved file has no `displayName` key

#### Scenario: The word clear as a name
- **WHEN** a builder runs `/map name <white>clear`
- **THEN** the display name is `<white>clear`, and the map is not cleared

### Requirement: A builder adds an online player as an author from the server
**Priority:** MoSCoW Must

WHEN a builder runs `/map author add <name>` and a player with that name is online, THE SYSTEM SHALL add that player's UUID and the
player's current name as an author, SHALL save the draft, and SHALL reply with the author's name. THE SYSTEM SHALL NOT call the profile
lookup for an online player.

#### Scenario: An online builder is credited
- **WHEN** the player `Seelenretterin` is online and a builder runs `/map author add Seelenretterin`
- **THEN** the draft holds an author with that player's UUID and the name `Seelenretterin`, and the draft is saved

### Requirement: A builder adds an offline player through the profile lookup off the tick
**Priority:** MoSCoW Must

WHEN a builder runs `/map author add <name>` and no online player has that name, THE SYSTEM SHALL resolve the name through the profile
lookup on a thread other than the tick thread, with a timeout of 5 seconds, and SHALL apply the result on the tick thread. The author
SHALL be the UUID in the dashed form and the name the profile service returns. THE SYSTEM SHALL NOT wait for the lookup on the tick
thread.

#### Scenario: An offline player is found
- **WHEN** no player named `Seelenretterin` is online, and the profile service returns the UUID `e1dff87a-1059-47f1-bc7d-533f072bf8c7` for that name
- **THEN** the draft holds an author with that UUID and the name the service returned, and the builder is told the author was added

#### Scenario: The lookup runs off the tick
- **WHEN** a builder runs `/map author add` for an offline player
- **THEN** the lookup runs on a thread other than the tick thread, and the draft is changed only when the result is applied on the tick thread

### Requirement: A failed lookup changes nothing
**Priority:** MoSCoW Must

IF the profile lookup finds no player, fails, or does not answer within 5 seconds, THEN THE SYSTEM SHALL leave the draft unchanged, SHALL NOT save,
and SHALL tell the builder which of the three cases applied.

#### Scenario: No player with that name
- **WHEN** the profile service reports no player for the name `Nobody_Here`
- **THEN** the draft is unchanged, the file is not written, and the builder is told that no player has that name

#### Scenario: The lookup times out
- **WHEN** the profile service does not answer within 5 seconds
- **THEN** the draft is unchanged, the file is not written, and the builder is told that the lookup timed out

### Requirement: A lookup result for a map that is no longer open is discarded
**Priority:** MoSCoW Must

IF a lookup result arrives after the builder has closed the map or opened another one, THEN THE SYSTEM SHALL discard the result, SHALL
change no draft, and SHALL tell the builder that the author was not added because the map was no longer open.

#### Scenario: The builder opens another map while the lookup runs
- **WHEN** a builder runs `/map author add Seelenretterin` on `blue_and_red` and then runs `/map open palm_beach` before the result arrives
- **THEN** `blue_and_red` is not changed, `palm_beach` is not changed, and the builder is told the author was not added

### Requirement: An author is never listed twice and a map has at most eight
**Priority:** MoSCoW Must

IF an add would give a map more than eight authors, THEN THE SYSTEM SHALL refuse it. IF an add names a player whose UUID is already an author,
THEN THE SYSTEM SHALL refuse it and change nothing. Both refusals SHALL name the limit or the existing author.

#### Scenario: Ninth author refused
- **WHEN** a map with eight authors receives `/map author add` for a ninth player
- **THEN** the draft is unchanged and the reply names the limit of eight

#### Scenario: Author already listed
- **WHEN** a map lists `TheMeinerLP` and a builder adds `TheMeinerLP` again
- **THEN** the draft is unchanged and the reply says the player is already an author

### Requirement: An author name must have the Minecraft username shape
**Priority:** MoSCoW Must

THE SYSTEM SHALL accept an author name only if it matches `[A-Za-z0-9_]{1,16}`. IF the name does not match, THEN THE SYSTEM SHALL refuse it before any
lookup is made.

#### Scenario: A path-like name makes no lookup
- **WHEN** a builder runs `/map author add ../../etc`
- **THEN** the reply names the name rule, no lookup is made, and the draft is unchanged

### Requirement: A builder removes an author by name
**Priority:** MoSCoW Must

WHEN a builder runs `/map author remove <name>`, THE SYSTEM SHALL remove the author whose name equals the given name ignoring case, SHALL save
the draft, and SHALL reply with the removed name. IF no author has that name, THEN THE SYSTEM SHALL change nothing and SHALL say so.

#### Scenario: Remove by a different letter case
- **WHEN** a map lists the author `TheMeinerLP` and a builder runs `/map author remove themeinerlp`
- **THEN** the author list is empty, the draft is saved, and the reply names `TheMeinerLP`

#### Scenario: Remove an author who is not listed
- **WHEN** a builder runs `/map author remove Nobody` on a map with one author
- **THEN** the author list is unchanged and the file is not written

### Requirement: The reference time is a finite number of seconds from zero to one hour
**Priority:** MoSCoW Must

WHEN a builder runs `/map time <seconds>`, THE SYSTEM SHALL accept a finite decimal number greater than zero and at most 3600, SHALL set the
draft's reference time, SHALL save the draft, and SHALL reply with the value. IF the value is not such a number, THEN THE SYSTEM SHALL leave the
draft unchanged and SHALL name the accepted range.

#### Scenario: A reference time of 45.5 seconds
- **WHEN** a builder runs `/map time 45.5`
- **THEN** the draft's reference time is 45.5 and the saved file holds `45.5`

#### Scenario: Zero and above the maximum are refused
- **WHEN** a builder runs `/map time 0` or `/map time 3601`
- **THEN** the draft is unchanged and the reply names the range from above zero to 3600

#### Scenario: Text that is not a number is refused
- **WHEN** a builder runs `/map time NaN`
- **THEN** the draft is unchanged and the reply names the accepted range

### Requirement: A metadata edit keeps the rest of the draft
**Priority:** MoSCoW Must

WHEN a display name, an author or the reference time changes, THE SYSTEM SHALL keep the draft's id, world, spawn, rings, boost configuration,
guide line, ring effects, and the fields the edit did not change.

#### Scenario: Name edit keeps rings and spawn
- **WHEN** a map with a spawn, three rings and two authors runs `/map name Blue & Red`
- **THEN** the saved draft has the same spawn, the same three rings and the same two authors, and the new display name

### Requirement: Every metadata change is saved before the builder sees it
**Priority:** MoSCoW Must

WHEN a metadata change is made, THE SYSTEM SHALL save the draft first and SHALL show the change only if the save succeeded. IF the save fails, THEN THE
SYSTEM SHALL keep the previous draft and SHALL tell the builder that the change was not saved.

#### Scenario: Save fails on a name change
- **WHEN** a builder runs `/map name Blue & Red` and the write fails
- **THEN** the draft in memory keeps its previous display name, and the builder sees the save-failed message

### Requirement: Each metadata subcommand is gated by the setup permission
**Priority:** MoSCoW Must

THE SYSTEM SHALL check `voyager.setup.use` for each of `/map name`, `/map author` and `/map time` separately, and IF the sender does not hold it,
THEN THE SYSTEM SHALL tell the sender the command is denied and SHALL change nothing.

#### Scenario: A builder without the node
- **WHEN** a player without `voyager.setup.use` runs `/map author add Seelenretterin`
- **THEN** no lookup is made, the draft is unchanged, and the player is told the command is denied

### Requirement: The new messages exist in English and German with the same placeholders
**Priority:** MoSCoW Must

THE SYSTEM SHALL provide every player-facing message of the metadata commands in `voyager_setup_en_US.properties` and in
`voyager_setup_de_DE.properties`, each with the same `<arg:N>` placeholders in both bundles.

#### Scenario: Bundle parity
- **WHEN** the setup bundles are compared key by key
- **THEN** every metadata message key exists in both bundles and the placeholder numbers of each key match
