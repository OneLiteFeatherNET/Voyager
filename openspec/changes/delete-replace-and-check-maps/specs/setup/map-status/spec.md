# Spec Delta

## Purpose

`/map status` tells the builder whether a map is ready in the game's own terms, including the terrain and the catalogue checks that
the game and the publish gate apply, and `/map medals` tells the builder the time thresholds of the map.

## MODIFIED Requirements

### Requirement: Status lists spawn, ring count and blocking problems
**Priority:** MoSCoW Must

WHEN a builder runs `/map status` in an open map, THE SYSTEM SHALL reply with the spawn state (set or not set), the ring count, the terrain
state from the region census, and one line per blocking problem. A blocking problem is a missing spawn, a draft with no rings, a world
with no full chunk, or a catalogue problem the game's own check reports for the map.

#### Scenario: Empty draft
- **WHEN** a builder runs `/map status` on a new draft
- **THEN** the reply says the spawn is not set, the ring count is 0, the terrain is empty, and lists the spawn and ring problems

#### Scenario: Complete draft
- **WHEN** a builder runs `/map status` on a draft with a spawn, 35 rings and a world with full chunks, and the game's check reports nothing
- **THEN** the reply says the spawn is set, the ring count is 35, and lists no problems

## ADDED Requirements

### Requirement: An empty void world is blocking
**Priority:** MoSCoW Must

WHEN a builder runs `/map status`, THE SYSTEM SHALL count the full chunks of the map's world with the region census, and IF the count is 0,
THEN THE SYSTEM SHALL list "the world has no built terrain" as a blocking problem. A partial chunk SHALL be reported but SHALL NOT block.

#### Scenario: Void world with no terrain
- **WHEN** a builder runs `/map status` on a map whose world is the unmodified void template
- **THEN** the reply lists the world as having no built terrain, as a blocking problem

### Requirement: The game's catalogue check runs off the tick
**Priority:** MoSCoW Must

WHEN a builder runs `/map status`, THE SYSTEM SHALL run the game's `CatalogValidation` and the census off the tick thread, SHALL reply when
both finish, and SHALL reply "the check did not finish" instead of a ready state IF either does not finish within 30 seconds. The check SHALL
NOT write any file.

#### Scenario: Check finishes
- **WHEN** a builder runs `/map status` and the catalogue check reports one problem for the map
- **THEN** the reply lists that problem as blocking, and the tick thread is not blocked while the check runs

#### Scenario: Check does not finish
- **WHEN** the check has not finished after 30 seconds
- **THEN** the reply says the check did not finish, and no ready state is shown

### Requirement: Medal thresholds are shown for the reference time
**Priority:** MoSCoW Should

WHEN a builder runs `/map medals` in an open map, THE SYSTEM SHALL reply with the four medal thresholds in seconds, computed from the map's reference
time and the global bracket multipliers, and SHALL change nothing.

#### Scenario: Default reference time
- **WHEN** a builder runs `/map medals` on a map with the reference time 60 seconds
- **THEN** the reply lists the four thresholds computed as 60 times 1.00, 1.10, 1.25 and 1.50 seconds, and no file changes
