# Spec Delta

## Purpose

The shipped catalogue gains one practice cup, so that a lone racer has a run to fly. The cup plays the shipped map's first map
and is started only by a solo wait (`race/waiting-lobby`). The rest of the shipped content is unchanged.

## MODIFIED Requirements

### Requirement: Exactly two cups ship, alpha_cup and legacy_cup
**Priority:** Must.

THE shipped catalogue SHALL provide exactly three cups, with the keys `elytrarace:alpha_cup`, `elytrarace:legacy_cup` and
`elytrarace:solo_practice`, and SHALL NOT ship `test_cup` or any other cup file. The `elytrarace:solo_practice` cup SHALL have
mode `PRACTICE` and SHALL name the same map as the first map of `elytrarace:alpha_cup`.

#### Scenario: the catalogue lists two cups
- **WHEN** the shipped data directory is loaded through the catalogue loader
- **THEN** the snapshot lists exactly three cups, `elytrarace:alpha_cup`, `elytrarace:legacy_cup` and `elytrarace:solo_practice`

#### Scenario: no other cup file is shipped
- **WHEN** the shipped `cups/` directory is listed
- **THEN** it contains `alpha_cup.json`, `legacy_cup.json` and `solo_practice.json` and no other cup file

#### Scenario: the practice cup plays the alpha map
- **WHEN** `elytrarace:solo_practice` is resolved from the shipped catalogue
- **THEN** its mode is `PRACTICE` and its maps are `elytrarace:blue_and_red` and nothing else
