# Spec Delta

## Purpose

A racer in the lobby must know whether the run they are about to fly counts. The sidebar title names the run as ranked or
practice, in the racer's language.

## ADDED Requirements

### Requirement: The lobby title names the run as ranked or practice
**MoSCoW:** Must. WHILE a racer waits in the lobby or in the first map's lobby, THE SYSTEM SHALL show in the sidebar title the
label `Ranked` for a RACE cup and `Practice · not ranked` for a practice cup, each translated in every bundled language. THE
SYSTEM SHALL keep the title within the sidebar's width in every bundled language.

#### Scenario: A ranked lobby
- **WHEN** a racer waits for a RACE cup
- **THEN** the sidebar title carries the label `Ranked`, in the racer's language

#### Scenario: A practice lobby
- **WHEN** a racer waits for the practice cup started from a solo wait
- **THEN** the sidebar title carries the label `Practice · not ranked`, in the racer's language

#### Scenario: The label fits the sidebar
- **WHEN** the longest bundled label is shown in the sidebar title
- **THEN** the title is not cut off in the sidebar's width
