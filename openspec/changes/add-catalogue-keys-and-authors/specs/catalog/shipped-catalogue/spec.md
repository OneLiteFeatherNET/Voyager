# Spec Delta

## Purpose

The shipped cups and maps are named by their keys in the elytrarace namespace. The set of shipped files does not change.

## MODIFIED Requirements

### Requirement: Exactly two cups ship, alpha_cup and legacy_cup
**Priority:** Must.

THE shipped catalogue SHALL provide exactly two cups, with the keys `elytrarace:alpha_cup` and `elytrarace:legacy_cup`, and
SHALL NOT ship `test_cup` or any other cup file.

#### Scenario: the catalogue lists two cups
- **WHEN** the shipped data directory is loaded through the catalogue loader
- **THEN** the snapshot lists exactly two cups, `elytrarace:alpha_cup` and `elytrarace:legacy_cup`

#### Scenario: no other cup file is shipped
- **WHEN** the shipped `cups/` directory is listed
- **THEN** it contains `alpha_cup.json` and `legacy_cup.json` and no other cup file

### Requirement: The alpha cup plays the one shipped map
**Priority:** Must.

THE `elytrarace:alpha_cup` cup SHALL name exactly one map in its `mapNames`, `elytrarace:blue_and_red`, and that key SHALL
resolve to the shipped map file.

#### Scenario: the rotation is the one map
- **WHEN** `elytrarace:alpha_cup` is resolved from the shipped catalogue
- **THEN** its maps are `elytrarace:blue_and_red` and nothing else

#### Scenario: a map name that resolves to nothing is refused
- **WHEN** a map key of `elytrarace:alpha_cup` has no shipped map file
- **THEN** the catalogue reports a problem naming the cup and the missing map, and the shipped catalogue does not load cleanly

### Requirement: The legacy cup plays the three converted legacy maps
**Priority:** Must.

THE shipped data directory SHALL contain `palm_beach.json`, `dragon_lobby.json` and `memoria.json` in `maps/`, and
`elytrarace:legacy_cup` SHALL name exactly the keys `elytrarace:palm_beach`, `elytrarace:dragon_lobby` and
`elytrarace:memoria`, in that order, in its `mapNames`. The `nether_sprint`, `frozen_cathedral` and `skyward_drift` map
files SHALL NOT be shipped until their worlds exist under the worlds path.

#### Scenario: the legacy maps ship and the cup plays them
- **WHEN** the shipped `maps/` directory is listed and `elytrarace:legacy_cup` is resolved
- **THEN** it contains `palm_beach.json`, `dragon_lobby.json` and `memoria.json`, and the cup plays exactly those three maps in order

#### Scenario: the converted maps without a world stay out
- **WHEN** the shipped `maps/` directory is listed
- **THEN** it contains none of `nether_sprint.json`, `frozen_cathedral.json` or `skyward_drift.json`
