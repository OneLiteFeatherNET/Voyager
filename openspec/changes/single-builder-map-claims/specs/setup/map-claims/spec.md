# Spec Delta

## Purpose

Two builders on one setup server must not edit one map at the same time, because each save writes the whole draft and the
last save would silently replace the other's rings. A builder who holds a map is its only editor until the builder closes
it, opens another map, or disconnects. A builder who is refused is told who holds the map.

## ADDED Requirements

### Requirement: One builder holds a map at a time
**Priority:** MoSCoW Must

WHILE a builder holds the claim of a map, THE SYSTEM SHALL refuse a claim of that map by any other builder, and SHALL keep
at most one holder for each map key.

#### Scenario: A second builder asks for a held map
- **WHEN** builder A holds `skyfortress` and builder B runs `/map open skyfortress`
- **THEN** the server refuses B's open, and A still holds `skyfortress`

#### Scenario: Two keys do not block each other
- **WHEN** builder A holds the map key `map:alpha` and builder B claims the key `cup:alpha`
- **THEN** both claims are granted

### Requirement: A refusal names the holder
**Priority:** MoSCoW Must

IF a builder opens a map that another builder holds, THEN THE SYSTEM SHALL refuse with a message that names the holder, and
SHALL change neither the builder's current map nor the draft file.

#### Scenario: Refusal names the holder
- **WHEN** builder B runs `/map open skyfortress` while builder A holds it
- **THEN** B sees a message that names A, B's current map is unchanged, and `drafts/skyfortress.json` or `maps/skyfortress.json` keeps its bytes

#### Scenario: Holder's name is the one recorded at the claim
- **WHEN** builder A took the claim under the name "Alpha" and later changes that name
- **THEN** the refusal names the builder as "Alpha"

### Requirement: The holder's re-open changes nothing
**Priority:** MoSCoW Must

WHEN a builder runs `/map open <id>` for the map the builder already holds, THE SYSTEM SHALL keep the builder's session, SHALL
NOT reload the draft, and SHALL reply as for a fresh open.

#### Scenario: Holder opens the same map again
- **WHEN** builder A holds `skyfortress`, has edited it since opening, and runs `/map open skyfortress` again
- **THEN** the session object is the same as before, the draft is not read from the store, and A sees the opened message

### Requirement: A claim is taken only after the world opens
**Priority:** MoSCoW Must

WHEN a builder opens a map, THE SYSTEM SHALL take the claim after the draft is loaded and the map's world is found, and
IF the world cannot be found, THEN THE SYSTEM SHALL take no claim.

#### Scenario: World cannot be found
- **WHEN** builder A runs `/map open skyfortress` and the world of the map cannot be found
- **THEN** the server refuses with the world's error, and no builder holds `skyfortress`

### Requirement: A claim is released by close, by opening another map, and by disconnect
**Priority:** MoSCoW Must

WHEN the holder of a claim runs `/map close`, opens another map, or disconnects, THE SYSTEM SHALL release that claim, and SHALL
release it only for the builder who holds it. THE SYSTEM SHALL take the new claim of another map before it releases the
previous one.

#### Scenario: Close releases the map
- **WHEN** builder A holding `skyfortress` runs `/map close`
- **THEN** builder B can open `skyfortress`, and A has no open map

#### Scenario: Open of another map releases the first
- **WHEN** builder A holding `skyfortress` runs `/map open canyon` and `canyon` is free
- **THEN** `skyfortress` is free and `canyon` is held by A

#### Scenario: Refused open keeps the first claim
- **WHEN** builder A holding `skyfortress` runs `/map open canyon` and B holds `canyon`
- **THEN** A still holds `skyfortress` and `canyon` is still held by B

#### Scenario: Disconnect releases the map
- **WHEN** builder A holding `skyfortress` disconnects
- **THEN** `skyfortress` is free

### Requirement: Claims are held in memory only
**Priority:** MoSCoW Must

THE SYSTEM SHALL keep claims in memory only, SHALL NOT write a claim to disk, and SHALL hold no claim after a start.

#### Scenario: Restart
- **WHEN** the setup server stops while builder A holds `skyfortress` and starts again
- **THEN** no builder holds `skyfortress` and no lock file exists in the data directory

### Requirement: Claims need the setup permission
**Priority:** MoSCoW Must

THE SYSTEM SHALL take a claim only for a builder who holds `voyager.setup.use`, and SHALL check that permission before any
claim is taken.

#### Scenario: Builder without the node
- **WHEN** a builder without `voyager.setup.use` runs `/map open skyfortress`
- **THEN** the builder is refused, and no claim of `skyfortress` exists

### Requirement: An import that fails releases its claim
**Priority:** MoSCoW Should

IF an import of a map ends with a failure, THEN THE SYSTEM SHALL release the claim that the import took on the map id.

#### Scenario: Import fails halfway
- **WHEN** an import of `canyon` fails after it has taken the claim of `canyon`
- **THEN** no builder holds `canyon`
