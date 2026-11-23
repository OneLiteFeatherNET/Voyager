# Spec Delta

## Purpose

A race result records whether its ticks were plausible, so that calibration and later review can read the flag from the database
without the game log.

## ADDED Requirements

### Requirement: A race result records its plausibility flag
**MoSCoW:** Should. WHEN a race result is written, THE SYSTEM SHALL store the largest excess of its run in the existing
`plausibility_error` column, and SHALL store it as null when no tick of the run was flagged.

#### Scenario: a flagged run
- **WHEN** a run had flagged ticks with a largest excess of 0.4 blocks
- **THEN** the stored `plausibility_error` is 0.4

#### Scenario: a clean run
- **WHEN** no tick of the run was flagged
- **THEN** `plausibility_error` is stored as null

#### Scenario: the check is off
- **WHEN** `VOYAGER_RING_PLAUSIBILITY` is `off`
- **THEN** `plausibility_error` is stored as null for every run
