# Spec Delta

## Purpose

A display name may carry any colour. A line that names a map or a cup has one accent for its own structure, and the colour of
the name is not counted as that accent. Error lines keep one fixed red, which is their colour and not a choice of the author.

## REMOVED Requirements

### Requirement: Each line uses one accent, and the red colour is never used

## ADDED Requirements

### Requirement: Each line uses one accent, and error lines use the fixed red
**MoSCoW:** Must. THE SYSTEM SHALL write each player-facing line with at most one accent palette tag besides the dim structure tag.
A display name's colour is not an accent for this rule, so a line that names a map or cup may carry the name's colour and one
accent tag. THE SYSTEM SHALL colour every error line with the fixed error red of `Palette`, and SHALL NOT use that red for any
other line.

#### Scenario: A line with two accents
- **WHEN** a bundle value uses two accent tags in one line, neither of them a display name
- **THEN** the style test fails and names the key

#### Scenario: A line that names a map
- **WHEN** the finish line names a map in its colour and carries one accent tag for the medal word
- **THEN** the style test accepts the line, because the name's colour is not counted

#### Scenario: An error line
- **WHEN** a reload fails
- **THEN** its line is in the fixed error red
