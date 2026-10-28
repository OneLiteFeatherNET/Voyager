# Spec Delta

## Purpose

A map and a cup have a display name that players read. The display name is an optional MiniMessage string in the catalogue
(`displayName`, added by `add-display-names`), so an author can colour it. A name without a colour of its own gets one of six
bright hues. This delta changes the plain-text rule of `add-display-names`: a display name is MiniMessage, and any colour is
allowed in it, red included.

## ADDED Requirements

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
