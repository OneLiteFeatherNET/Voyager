# Spec Delta

## Purpose

Adds the checkpoint rings to the shipped maps. A `CHECKPOINT` ring is the target of a reset (see
`race-course-reset`), so the shipped maps mark roughly every eighth ring. The boost rings of `elytraraceblueandred`
keep their indices; the checkpoint rings of that map are listed in the modified scenario.

## ADDED Requirements

### Requirement: The shipped maps mark checkpoint rings roughly every eighth ring
Priority: Must.

THE shipped maps `elytraraceblueandred`, `palmbeach`, `dragonlobby` and `memoria` SHALL mark roughly every eighth ring as
type `CHECKPOINT`, counting the 0-based ring index (the position in the ring array) as 7, 15, 23 and so on. A chosen index
whose ring is `BOOST` SHALL move to the nearest `STANDARD` ring, trying distance 1, then 2, and so on, with a tie going to
the earlier ring. The last ring of a map SHALL NOT be marked. Owner decision, 2026-10-13.

#### Scenario: the checkpoint rings of the alpha map are marked
- **WHEN** `elytraraceblueandred` is loaded
- **THEN** its `CHECKPOINT` rings are at indices 7, 15, 23 and 31, and its `BOOST` rings are still at 0, 18 and 27

#### Scenario: the checkpoint rings of the legacy maps are marked
- **WHEN** `palmbeach`, `dragonlobby` and `memoria` are loaded
- **THEN** each has its `CHECKPOINT` rings at indices 7, 15 and 23, and no `BOOST` ring moved

#### Scenario: a checkpoint ring has no effect in play
- **WHEN** a racer passes a `CHECKPOINT` ring of a shipped map
- **THEN** the ring applies no effect, and the checkpoint is recorded for a later reset

## MODIFIED Requirements

### Requirement: The shipped map carries at least two BOOST rings
Priority: Must.

THE shipped map `elytraraceblueandred` SHALL carry at least two rings of type `BOOST`, so that the boost ring effect is
exercised by the alpha.

#### Scenario: the boost rings are present
- **WHEN** `elytraraceblueandred` is loaded
- **THEN** at least two of its rings have type `BOOST`

#### Scenario: the boost rings are the chosen ones
- **WHEN** `elytraraceblueandred` is loaded
- **THEN** its `BOOST` rings are at indices 0, 18 and 27, and every other ring is `STANDARD`, except the `CHECKPOINT` rings at indices 7, 15, 23 and 31
