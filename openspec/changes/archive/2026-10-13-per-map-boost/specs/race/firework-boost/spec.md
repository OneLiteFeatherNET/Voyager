# Spec Delta

## Purpose

Defines the per-map firework boost: how many rockets a racer carries, what the rocket item says about its flight duration,
which of the boost values a map states and how they are shown to the operator and to the designer. The burn and cooldown
values already exist per map; this delta fixes their meaning and makes them visible.

## ADDED Requirements

### Requirement: A racer carries the number of rockets the map states
**Priority:** MoSCoW Must. WHEN a map's race begins and a racer is equipped, THE SYSTEM SHALL give the racer
`boostConfig.rocketsPerRacer` rockets, which is 64 when the map states no count.

#### Scenario: A map with a count
- **WHEN** a map states `"rocketsPerRacer": 12`
- **THEN** a racer equipped for that map holds a stack of 12 rockets

#### Scenario: A map without a count
- **WHEN** a map states no `rocketsPerRacer`
- **THEN** a racer holds a stack of 64 rockets, as before this change

#### Scenario: A count outside the stack limit is refused
- **WHEN** a map states `"rocketsPerRacer": 65`
- **THEN** the file is refused and the message names `rocketsPerRacer`

### Requirement: The rocket item states the flight duration its burn corresponds to
**Priority:** MoSCoW Should. THE SYSTEM SHALL write into the rocket item a flight duration of
`clamp(round(burnDurationTicks / 10), 1, 3)`, so that the item's tooltip agrees with the burn the server applies.

#### Scenario: The default burn
- **WHEN** a map's burn is 30 ticks
- **THEN** the rocket item states flight duration 3

#### Scenario: A short burn
- **WHEN** a map's burn is 10 ticks
- **THEN** the rocket item states flight duration 1

### Requirement: The burn and cooldown of a map are the values the server applies
**Priority:** MoSCoW Must. WHILE a map is being raced, THE SYSTEM SHALL end each rocket's burn after exactly
`burnDurationTicks` server ticks and SHALL refuse another boost until `cooldownTicks` ticks have passed since that burn
started, using the values of the map being raced.

#### Scenario: A boost on a map with a different burn
- **WHEN** a racer boosts on a map whose burn is 40 ticks, and the next map's burn is 20 ticks
- **THEN** the boost runs for 40 ticks, and the next map's values apply only to boosts started on it

### Requirement: The map's boost tuning is stated at the start of the map
**Priority:** MoSCoW Must. WHEN a map's start countdown begins, THE SYSTEM SHALL write one log line that states the map's
burn duration, cooldown, rocket count, speed cap (or that there is none) and the BOOST and SLOW factors.

#### Scenario: The line names every value
- **WHEN** a map with the default values starts its countdown
- **THEN** the log line states burn 30, cooldown 40, rockets 64, no speed cap, BOOST 1.5 and SLOW 0.5

### Requirement: Every boost field is documented for the designer
**Priority:** MoSCoW Should. THE SYSTEM SHALL describe each boost field and each ring-effect field in
`map.schema.json` and in a reference page under `docs/reference/`, with its type, bounds, default and meaning.

#### Scenario: The schema describes the new fields
- **WHEN** a designer opens a map file in an editor that uses `map.schema.json`
- **THEN** each new field shows its description and the editor reports values outside their bounds
