# message-fitness Specification

## Purpose
Defines what the build refuses about player-facing text: that every key is translated in every locale with the same
placeholders, and that no code outside the translation layer builds a player-facing string. These are the two rules
that keep "every message is translatable" true after the next change, not only today.

## Requirements

### Requirement: The build fails when a locale is missing a key or disagrees with English about a placeholder
**MoSCoW:** Must. THE BUILD SHALL fail when, for either product (server or setup), a locale bundle lacks a key defined in
`en_US`, holds a key `en_US` does not define, or has a different multiset of `<arg:N>` indices or palette tags for a key.

#### Scenario: A German bundle missing one key
- **WHEN** `voyager_de_DE.properties` has no line for `voyager.result.cup.departed`
- **THEN** `MessageBundleParityTest` fails and names the key and the locale

#### Scenario: A German bundle with an extra key
- **WHEN** `voyager_setup_de_DE.properties` defines a key that `voyager_setup_en_US.properties` does not
- **THEN** the parity test fails and names the key

#### Scenario: A German bundle that is complete
- **WHEN** every key of both products exists in both locales with equal placeholders
- **THEN** the parity test passes

### Requirement: Every key a factory names exists in every locale
**MoSCoW:** Must. THE BUILD SHALL fail when a key constant in `Messages` or `SetupMessages` has no line in a locale bundle
of the product that sends it.

#### Scenario: A key named in code but not in the setup bundle
- **WHEN** `SetupMessages` names `voyager.command.denied` and the setup German bundle lacks it
- **THEN** the parity test fails and names the key and the bundle

### Requirement: No code outside the translation layer builds a player-facing string
**MoSCoW:** Must. THE BUILD SHALL fail when code in `net.elytrarace.voyager..` outside `net.elytrarace.voyager.platform.text`
calls `Component.text` with a literal, calls `Component.translatable` with a literal key, or calls
`MiniMessage.deserialize`, except for `RaceCommand`'s operator diagnostic as named in the rule.

#### Scenario: A literal in a call site
- **WHEN** a class in `voyager-platform` calls `Component.text("Ring 12/35")`
- **THEN** the architecture test fails and names the class

#### Scenario: A key named outside the message layer
- **WHEN** a class in `voyager-setup` calls `Component.translatable("voyager.setup.new.key")`
- **THEN** the architecture test fails and names the class

#### Scenario: The rule matches its own seeded violation
- **WHEN** the rule runs against a test source set that contains a seeded violation
- **THEN** the rule reports the violation, proving it is not vacuously true (`allowEmptyShould(false)`)

### Requirement: The operator status output is an explicit, argued exception
**MoSCoW:** Should. THE RULE SHALL exempt `RaceCommand`'s `/race` status output by class name, and the exemption SHALL
also name `WaitingRoom.describe()`, which that output prints, or the room line SHALL become a key.

#### Scenario: The room line
- **WHEN** `/race` prints the room status
- **THEN** the room line is either a translated key or is covered by the named exemption with its reason written in the rule
