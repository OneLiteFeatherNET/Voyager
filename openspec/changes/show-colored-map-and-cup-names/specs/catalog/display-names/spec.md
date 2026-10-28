# Spec Delta

## Purpose

A map and a cup have a display name that players read. The display name is an optional MiniMessage string in the catalogue
(`displayName`, added by `add-display-names`), so an author can colour it. A name without a colour gets one from a palette
of vivid hues that never includes red, because the course marks its finish in red. This delta changes the plain-text rule
of `add-display-names`: a display name is MiniMessage, restricted to colour and decoration tags.

## ADDED Requirements

### Requirement: A display name is MiniMessage with colour and decoration tags only
**MoSCoW:** Must. THE SYSTEM SHALL read a display name as MiniMessage and accept only the colour tags (named colours, hexadecimal
colours and `gradient`), the decoration tags (`bold`, `italic`, `underlined`, `strikethrough`, `obfuscated`) and `reset`.
IF a display name contains any other tag, THEN THE SYSTEM SHALL refuse the file and the message SHALL name the file, the field
and the tag.

#### Scenario: A coloured name loads
- **WHEN** a map file states `"displayName": "<blue>Blue</blue> & <red>Red</red>"`
- **THEN** the file is not refused for its tags, subject to the red rule below

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

### Requirement: A display name may not claim red
**MoSCoW:** Must. THE SYSTEM SHALL refuse a display name that uses the red colour in any form: the named colours `red` and
`dark_red`, a hexadecimal colour whose hue is in the red band, or a gradient or rainbow with a red stop. The refusal names the
file, the field and the colour. The course's finish is red, and no screen surface may claim red (`Palette`).

#### Scenario: A named red is refused
- **WHEN** a map file states `"displayName": "<red>Red</red>"`
- **THEN** the file is refused and the message names the file, `displayName` and the colour `red`

#### Scenario: A hexadecimal red is refused
- **WHEN** a cup file states `"displayName": "<#e0201a>Rush</#e0201a>"`
- **THEN** the file is refused, because the hue is in the red band

### Requirement: A name without a colour is shown in a palette hue chosen by its key
**MoSCoW:** Must. WHEN a display name sets no colour, THE SYSTEM SHALL colour it with one hue from the name palette, chosen by a
stable hash of the key, so the same key gets the same hue on every server and every boot. The name palette holds only hues
that are not red and are not the colour of any other role in `Palette`. The hue applies to the whole name, and a colour the
name sets itself is kept.

#### Scenario: The same key gets the same hue
- **WHEN** the map `elytrarace:blue_and_red` with no colour is shown on two servers
- **THEN** both show it in the same palette hue

#### Scenario: A coloured name keeps its colour
- **WHEN** a display name sets `<gold>` on its first word and nothing on the second
- **THEN** the first word is gold and the second word takes the palette hue

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
