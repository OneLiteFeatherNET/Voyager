# Spec Delta

## Purpose

Defines how a player-facing message is chosen for a player's locale: which bundle answers, in what order the fallbacks
are tried, and what a message keeps when it is translated. It is the contract between the translation files and the
game, and it is what a translator can rely on.

## ADDED Requirements

### Requirement: Every player sees the game in the language of their client, with English as the fallback
**MoSCoW:** Must. WHEN the server renders a player-facing message for a player, THE SYSTEM SHALL choose the bundle along
the chain of the player's locale tag, then its language alone, then `en_US`, and SHALL render the message in the first
bundle that holds its key.

#### Scenario: A German client gets German
- **WHEN** a player whose client locale is `de_DE` receives `voyager.lobby.waiting`
- **THEN** the message is rendered from the German bundle

#### Scenario: A regional German client gets German
- **WHEN** a player whose client locale is `de_AT` receives `voyager.lobby.waiting`
- **THEN** the message is rendered from the German bundle, because the language `de` is tried after `de_AT`

#### Scenario: An unsupported client gets English
- **WHEN** a player whose client locale is `fr_FR` receives `voyager.lobby.waiting`
- **THEN** the message is rendered from `en_US`

### Requirement: A player whose locale is not yet known is shown English
**MoSCoW:** Must. WHILE a player's client has not yet sent its settings, THE SYSTEM SHALL render that player's messages
in `en_US`, whatever the server host's JVM default locale is.

#### Scenario: A German host and a player with no settings yet
- **WHEN** the server runs with the JVM default locale `de_DE` and a player's settings have not arrived
- **THEN** that player's messages are rendered in `en_US`

### Requirement: A missing German line falls back to English for that line only
**MoSCoW:** Must. IF a key is absent from the bundle of the chosen locale, THEN THE SYSTEM SHALL render that key from
`en_US` and SHALL NOT render the dotted key itself. THE SYSTEM SHALL log one warning per key and locale at boot.

#### Scenario: One line missing in German
- **WHEN** `voyager.lobby.cancelled` is absent from the German bundle and a German player receives it
- **THEN** the English text is shown, and the boot log holds one warning that names the key and the locale

### Requirement: Translations keep the arguments and colours of the English line
**MoSCoW:** Must. THE SYSTEM SHALL refuse at boot any bundle whose value for a key uses a different set of `<arg:N>`
indices or a different set of palette tags from the `en_US` value of the same key, and the refusal SHALL name the key and
the locale.

#### Scenario: A German line that drops an argument
- **WHEN** the German value of `voyager.lobby.waiting` contains `<arg:0>` but not `<arg:1>`
- **THEN** the boot refuses, and the message names `voyager.lobby.waiting` and `de_DE`

#### Scenario: A German line that recolours
- **WHEN** the German value of `voyager.result.map.dnf` uses `<c-speed>` where the English value uses `<c-warn>`
- **THEN** the boot refuses, and the message names the key and the locale

### Requirement: The `{N}` placeholder is refused in every locale
**MoSCoW:** Must. IF any bundle value contains `{` followed by a digit and `}`, THEN THE SYSTEM SHALL refuse to load that
bundle, as it does today for `en_US`.

#### Scenario: A German value with a MessageFormat placeholder
- **WHEN** a German value contains `{0}`
- **THEN** the load fails and the message names the key and the value

### Requirement: Plural forms are two keys, chosen by the count
**MoSCoW:** Should. WHERE an English key has a `.one` sibling, THE SYSTEM SHALL choose `<key>.one` when the count is 1
and `<key>.other` otherwise, and every locale SHALL define both keys.

#### Scenario: One ring
- **WHEN** a map with one ring is named in `voyager.countdown.subtitle`
- **THEN** the singular form is rendered

### Requirement: Setup messages reach the setup server's players in their locale
**MoSCoW:** Must. THE SYSTEM SHALL render every message the setup server sends, including the shared `Messages.commandDenied()`
key, from the setup server's own bundle, and no setup player SHALL see a dotted key.

#### Scenario: An unauthorised setup user
- **WHEN** a player without the setup permission runs a setup command on the setup server
- **THEN** the refusal is shown as text in the player's locale, not as `voyager.command.denied`

### Requirement: Console and operator output stays English
**MoSCoW:** Should. THE SYSTEM SHALL keep log lines and console replies in English. (Pending owner answer OQ-1 in
`proposal.md`; this requirement is the proposal's default.)

#### Scenario: A console reply
- **WHEN** an operator runs an unknown command in the console
- **THEN** the reply is English text and is not translated
