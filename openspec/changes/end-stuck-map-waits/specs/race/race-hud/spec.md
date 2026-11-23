# Spec Delta

## Purpose

A racer still flying after the first finish needs to know when the map will end, so the grace window is shown on the boss bar.

## ADDED Requirements

### Requirement: The boss bar shows the grace window to a racer still racing
**MoSCoW:** Should. WHILE a grace window of the map runs and a racer has not finished it, THE SYSTEM SHALL show on that racer's boss bar
the translated label for the remaining grace time, in whole seconds, and SHALL show the racer's own boss bar tier as before. THE SYSTEM
SHALL show no grace label before the first finish or after the window ends.

#### Scenario: The grace label appears after the first finish
- **WHEN** another racer finishes the map and the grace window begins with 90 seconds
- **THEN** a racer still racing sees a boss bar label naming the grace time remaining, starting at 90 seconds

#### Scenario: No label before a finish
- **WHEN** no racer has finished the map
- **THEN** the boss bar shows no grace label

#### Scenario: The label is translated
- **WHEN** the grace label is shown
- **THEN** it exists in English and German with the same placeholders, as `race/race-hud` requires for every HUD label
