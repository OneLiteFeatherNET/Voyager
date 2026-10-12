# Delta for server/com-settings

New capability: the two settings that control computer racers, their defaults and ranges, their configuration check, and
the boot line that states them. The movement and lifecycle rules are in `race/com-racers`.

## ADDED Requirements

### Requirement: The computer racer count is an optional setting that defaults to zero
**MoSCoW:** Must. THE SYSTEM SHALL read the setting `VOYAGER_COM_COUNT` as a whole number from 0 to 15, and SHALL use 0 when
it is absent or blank.

#### Scenario: Absent setting
- **WHEN** `VOYAGER_COM_COUNT` is not set
- **THEN** the computer racer count is 0

#### Scenario: Count above the cap is refused
- **WHEN** `VOYAGER_COM_COUNT` is 16
- **THEN** the configuration check fails and names `VOYAGER_COM_COUNT` and the range 0 to 15

#### Scenario: Count that is not a number is refused
- **WHEN** `VOYAGER_COM_COUNT` is `five`
- **THEN** the configuration check fails and names `VOYAGER_COM_COUNT` and the value it read

### Requirement: The difficulty is an optional setting that defaults to NORMAL
**MoSCoW:** Must. THE SYSTEM SHALL read `VOYAGER_COM_DIFFICULTY` as one of `EASY`, `NORMAL` or `HARD`, case-insensitively,
and SHALL use `NORMAL` when it is absent or blank.

#### Scenario: Absent difficulty
- **WHEN** `VOYAGER_COM_DIFFICULTY` is not set
- **THEN** the difficulty is `NORMAL`

#### Scenario: Unknown difficulty is refused
- **WHEN** `VOYAGER_COM_DIFFICULTY` is `expert`
- **THEN** the configuration check fails and lists `EASY`, `NORMAL` and `HARD`

### Requirement: The configuration check reports computer racer settings at boot
**MoSCoW:** Should. WHEN the server boots, THE SYSTEM SHALL log one line that states the computer racer count and the
difficulty, and SHALL log nothing about them when the count is 0.

#### Scenario: Boot line when enabled
- **WHEN** the server boots with `VOYAGER_COM_COUNT` of 5 and `VOYAGER_COM_DIFFICULTY` of `HARD`
- **THEN** one boot line states five computer racers at HARD

#### Scenario: No boot line when disabled
- **WHEN** the server boots with `VOYAGER_COM_COUNT` of 0
- **THEN** no boot line about computer racers is logged

### Requirement: Computer racer settings are read only by the composition root
**MoSCoW:** Must. THE SYSTEM SHALL read `VOYAGER_COM_COUNT` and `VOYAGER_COM_DIFFICULTY` only in `ServerSettings` and the
composition root, and pass the values to the computer racer code as plain values.

#### Scenario: Settings do not leak into the race
- **WHEN** a reviewer searches for the two property names
- **THEN** they appear only in `server.config` and `server.inject`, enforced by the rule in `architecture/architecture-enforcement`

### Requirement: A count of zero wires the no-op competitor field
**MoSCoW:** Must. WHEN `VOYAGER_COM_COUNT` is 0, THE SYSTEM SHALL wire the no-op competitor field, so that no computer racer
code runs in a cup.

#### Scenario: No-op field when disabled
- **WHEN** the server boots with `VOYAGER_COM_COUNT` of 0
- **THEN** the cup holds the no-op competitor field and creates no `ComField`
