# Spec Delta

## Purpose

A refusal of a `/map` command reaches the builder in the builder's language and names its cause. The text of a domain
exception is developer-facing, so it is never shown to a builder, and a missing draft tells the builder what to run next.

## MODIFIED Requirements

### Requirement: Opening a map loads its draft
**Priority:** MoSCoW Must

WHEN a builder runs `/map open <id>` for an existing draft, THE SYSTEM SHALL load the draft from `maps/<id>.json` or from
`drafts/<id>.json`, SHALL open the map world at `<worldsPath>/<id>`, SHALL show the ring previews, and SHALL give the builder
the wand if the builder does not hold one. IF another builder holds the map, THEN THE SYSTEM SHALL refuse the open and name
the holder, as `setup/map-claims` states. IF no draft exists for the id, THEN THE SYSTEM SHALL refuse the open, SHALL name
the id, and SHALL tell the builder to run `/map new <id>` to create it.

#### Scenario: Open a draft with three rings
- **WHEN** a builder runs `/map open skyfortress` and the file holds three rings
- **THEN** the builder stands in the map, sees three previews, and the wand is in the builder's inventory

#### Scenario: Open a missing or malformed draft
- **WHEN** a builder runs `/map open <id>` and no draft exists for the id, or the draft file is not valid JSON
- **THEN** the server refuses with a message naming the id; for a missing draft the message tells the builder to run `/map new <id>`; the refusal text contains no English exception message; and the builder's current map is unchanged

#### Scenario: Open a map another builder holds
- **WHEN** a builder runs `/map open skyfortress` and another builder holds `skyfortress`
- **THEN** the server refuses with a message naming the holder, and the builder's current map is unchanged

## ADDED Requirements

### Requirement: A refusal names its cause without raw exception text
**Priority:** MoSCoW Must

WHEN a `/map` command is refused because of a domain exception, THE SYSTEM SHALL reply with the message key that the cause maps
to, with the arguments that key names, in the builder's language. THE SYSTEM SHALL NOT send the message text of an exception to
a builder. An invalid map id SHALL get the same invalid-id message in `new`, `open` and `unlock`.

#### Scenario: Invalid id in open
- **WHEN** a builder runs `/map open ../etc`
- **THEN** the reply is the invalid-id message that names `../etc`, the same message that `/map new ../etc` gives

#### Scenario: German builder
- **WHEN** a builder whose client language is German runs `/map open canyon` and no draft exists
- **THEN** the reply is the German not-found message, and it contains no English sentence from the domain exception

#### Scenario: Refusal text is checked by a rule, not by reading
- **WHEN** the build runs the `voyager-fitness` architecture rules
- **THEN** no class of the setup adapter passes the message of an exception to `SetupMessages.refused`, and the rule fails if one does
