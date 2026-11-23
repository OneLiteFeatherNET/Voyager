# Spec Delta

## Purpose

The shipped content stops being a first draft. Reference times are measured from human runs, the alpha map's rings vary, each map
sets its own rocket count and speed cap, the notes describe the files they sit in, and a three-map development cup gives the cup an
arc. No engine rule changes: every field used here already exists in the map and cup formats.

## MODIFIED Requirements

### Requirement: Exactly two cups ship, alpha_cup and legacy_cup
**Priority:** Must.

THE shipped catalogue SHALL provide exactly four cups, with the keys `elytrarace:alpha_cup`, `elytrarace:legacy_cup`,
`elytrarace:solo_practice` and `elytrarace:dev_cup`, and SHALL NOT ship `test_cup` or any other cup file. The `elytrarace:dev_cup`
cup SHALL be shipped only once its three maps have a world and a spawn in the shipped data.

#### Scenario: the catalogue lists two cups
- **WHEN** the shipped data directory is loaded through the catalogue loader
- **THEN** the snapshot lists exactly four cups, `elytrarace:alpha_cup`, `elytrarace:legacy_cup`, `elytrarace:solo_practice` and `elytrarace:dev_cup`

#### Scenario: no other cup file is shipped
- **WHEN** the shipped `cups/` directory is listed
- **THEN** it contains `alpha_cup.json`, `legacy_cup.json`, `solo_practice.json` and `dev_cup.json` and no other cup file

### Requirement: The shipped map carries at least two BOOST rings
Priority: Must.

THE shipped map `elytraraceblueandred` SHALL carry at least two rings of type `BOOST`, so that the boost ring effect is
exercised by the alpha.

#### Scenario: the boost rings are present
- **WHEN** `elytraraceblueandred` is loaded
- **THEN** at least two of its rings have type `BOOST`

#### Scenario: the boost rings are the chosen ones
- **WHEN** `elytraraceblueandred` is loaded
- **THEN** its `BOOST` rings are at indices 0, 18 and 27, and every other ring is `STANDARD`, except the `CHECKPOINT` rings at indices 7,
  15, 23 and 31, the one `SLOW` ring named in its notes, and the one bonus ring named in its notes

### Requirement: Seeded values are marked provisional in the file
Priority: Should.

THE shipped map's `notes` SHALL mark its reference time, its per-ring points, its guide-line values and its BOOST ring choice as
provisional until a calibration record for that value exists under "Reference times are calibrated from human runs", so that the
balancing pass and the playtest can tell them from measured values.

#### Scenario: the BOOST choice is marked
- **WHEN** the shipped map's notes are read
- **THEN** the notes name rings 0, 18 and 27 as provisional BOOST rings and say they are to be tuned after the playtest, until the
  playtest records a calibration

## ADDED Requirements

### Requirement: Reference times are calibrated from human runs
Priority: Must.

A map's `referenceTimeSeconds` SHALL be calibrated only from at least ten human runs of that map, as the top-quartile median of those
runs. A calibrated map's `notes` SHALL state the sample count and the date of the calibration. A map with fewer than ten runs SHALL keep
its reference time marked provisional under "Seeded values are marked provisional in the file".

#### Scenario: a calibrated map states its sample
- **WHEN** a map's reference time is calibrated from twelve human runs on a given date
- **THEN** its notes state the count of twelve, the date, and that the value is the top-quartile median

#### Scenario: a map with too few runs stays provisional
- **WHEN** a map has nine human runs
- **THEN** its reference time is still marked provisional in its notes

### Requirement: The shipped notes match the ring types and points in the file
Priority: Must.

THE `notes` of each shipped map SHALL describe the ring types, ring counts and per-ring points that the map file actually carries, so
that no note claims a value the file does not hold.

#### Scenario: the legacy maps' ring types are described correctly
- **WHEN** `palmbeach`, `dragonlobby` and `memoria` are read
- **THEN** their notes do not say that all rings are `STANDARD`, and they name their `CHECKPOINT` rings

### Requirement: Each shipped map states its rocket count and speed cap
Priority: Should.

THE shipped map SHALL state `rocketsPerRacer` and `maxSpeedBlocksPerTick` in its file, and the `maxSpeedBlocksPerTick` SHALL be derived
from the map's path length divided by its reference time, as its notes say. The rocket count of a map of 60 to 80 seconds SHALL be
between 4 and 6, and that of `dragonlobby` SHALL be 8.

#### Scenario: the alpha map's speed cap is stated
- **WHEN** `elytraraceblueandred.json` is read
- **THEN** it carries a `maxSpeedBlocksPerTick` and its notes give the path length and reference time it was derived from

#### Scenario: the rocket count is within the band
- **WHEN** a shipped map of 60 to 80 seconds is read
- **THEN** its `rocketsPerRacer` is between 4 and 6 inclusive

### Requirement: The development cup plays three maps in difficulty order
Priority: Should.

THE `elytrarace:dev_cup` cup SHALL name the maps `elytrarace:nether_sprint`, `elytrarace:frozen_cathedral` and `elytrarace:skyward_drift`,
in that order, and its mode SHALL be `RACE`.

#### Scenario: the development cup plays its three maps in order
- **WHEN** `elytrarace:dev_cup` is resolved from the shipped catalogue
- **THEN** its maps are the three named maps in that order, and each resolves to a shipped map file
