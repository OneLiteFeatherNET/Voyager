# Spec Delta

## Purpose

A display name is the one place a line may carry its own colour beside the accent of the line. The rule that a line has one
accent and that red is never used stays, and it is stated for names as well.

## MODIFIED Requirements

### Requirement: Each line uses one accent, and the red colour is never used
**MoSCoW:** Must. THE SYSTEM SHALL write each player-facing line with at most one accent palette tag besides the dim structure tag,
and SHALL NOT use a red colour in any bundle value, in line with the course's own red finish. A display name's colour is not an
accent for this rule, so a line that names a map may carry the name's colour and one accent tag. A display name SHALL NOT use red
(see `catalog/display-names`).

#### Scenario: A line with two accents
- **WHEN** a bundle value uses two accent tags in one line
- **THEN** the style test fails and names the key

#### Scenario: A line that names a map
- **WHEN** the finish line names a map in its colour and carries one accent tag for the medal word
- **THEN** the style test accepts the line, because the name's colour is not counted

#### Scenario: A display name in red
- **WHEN** a display name uses red
- **THEN** the catalogue refuses the file, so no line can show it
