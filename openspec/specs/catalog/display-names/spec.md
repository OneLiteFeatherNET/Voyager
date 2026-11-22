# catalog/display-names Specification

## Purpose
A cup and a map have a name that players read. The catalogue gives each one an optional display name, shows it in place
of the id on every player-facing surface, and never translates it, because a display name is a proper noun.

## Requirements

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

### Requirement: A display name is MiniMessage with colour, rainbow, decoration and reset tags only
**MoSCoW:** Must. THE SYSTEM SHALL read a display name as MiniMessage and accept only the colour tags (named colours, hexadecimal
colours, `gradient` and `rainbow`), the decoration tags (`bold`, `italic`, `underlined`, `strikethrough`, `obfuscated`) and
`reset`. IF a display name contains any other tag, THEN THE SYSTEM SHALL refuse the file and the message SHALL name the file, the
field and the tag.

#### Scenario: A coloured name loads
- **WHEN** a map file states `"displayName": "<blue>Blue</blue> & <gold>Gold</gold>"`
- **THEN** the file loads and the name is shown with those colours

#### Scenario: A click tag is refused
- **WHEN** a cup file states `"displayName": "<click:run_command:/stop>Alpha</click>"`
- **THEN** the file is refused and the message names the file, the field `displayName` and the tag `click`

#### Scenario: A translatable tag is refused
- **WHEN** a map file states `"displayName": "<lang:block.minecraft.stone>Blue</lang>"`
- **THEN** the file is refused and the message names the tag `lang`

### Requirement: A display name is at most forty visible characters
**MoSCoW:** Must. THE SYSTEM SHALL measure a display name's length in the characters a player sees, with the MiniMessage tags
not counted. IF the visible text is longer than forty code points, THEN THE SYSTEM SHALL refuse the file and name the file and
the field. A name is never cut short.

#### Scenario: Tags do not count
- **WHEN** a display name is `<gradient:#00c2ff:#5bff8f>Blue and Red Rush</gradient>`
- **THEN** its visible length is 17 and it is accepted

#### Scenario: Forty-one visible characters are refused
- **WHEN** a display name shows forty-one characters after its tags are removed
- **THEN** the file is refused and the message names `displayName` and the file

### Requirement: A display name may use any colour, including red
**MoSCoW:** Must. THE SYSTEM SHALL accept a display name in any colour, red included, and SHALL NOT refuse a name for its hue.

#### Scenario: A named red loads
- **WHEN** a map file states `"displayName": "<red>Red</red>"`
- **THEN** the file loads and the name is shown in red

#### Scenario: A rainbow loads
- **WHEN** a cup file states `"displayName": "<rainbow>Rush</rainbow>"`
- **THEN** the file loads and the name is shown in a rainbow

### Requirement: A name without a colour of its own takes one of six bright hues, chosen by its key
**MoSCoW:** Must. WHEN a display name sets no colour, THE SYSTEM SHALL colour the whole name with one hue of the default palette,
chosen by a stable hash of the key. THE PALETTE SHALL be the six hues listed in the scenarios, each at a contrast ratio of at least
6:1 on `#1A1A1A`.

#### Scenario: The same key gets the same hue
- **WHEN** the map `elytrarace:blue_and_red` with no colour is shown on two servers
- **THEN** both show it in the same palette hue

#### Scenario: A coloured name keeps its colour
- **WHEN** a display name sets `<gold>` on its first word and nothing on the second
- **THEN** the first word is gold and the second word takes the palette hue

#### Scenario: Every palette hue is readable
- **WHEN** each hue of the palette is measured against `#1A1A1A`
- **THEN** every hue reaches a contrast ratio of 6:1 or more

### Requirement: A missing display name is shown as the key's value in title case, never as the key
**MoSCoW:** Must. WHEN a map or cup has no display name, THE SYSTEM SHALL show its key's value with each underscore or hyphen
replaced by a space and each word capitalised, and SHALL NOT show the raw key. THE SYSTEM SHALL log one warning at boot for each
played cup or map without a display name, naming its key.

#### Scenario: A map without a name
- **WHEN** the map `elytrarace:dragon_lobby` has no display name
- **THEN** it is shown as "Dragon Lobby" and the boot log warns about `elytrarace:dragon_lobby`

### Requirement: Names are data, never translated, and parsed only in the text layer
**MoSCoW:** Must. THE SYSTEM SHALL pass a display name to the translation layer as a parsed component argument, and SHALL NOT look
a name up as a translation key. The MiniMessage parse of a display name SHALL live in `net.elytrarace.voyager.platform.text`.

#### Scenario: A name with angle brackets that is not a tag
- **WHEN** a display name is `a < b` with no tag
- **THEN** the name is shown as the text `a < b`
