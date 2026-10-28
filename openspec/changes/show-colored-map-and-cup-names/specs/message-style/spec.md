# Spec Delta

## Purpose

A display name's colour is not counted as an accent of its line, and a display name may use any colour. The bundle rules for
accents and for red in bundle values are unchanged, and error lines keep the colour they have today.

## MODIFIED Requirements

### Requirement: Each line uses one accent, and the red colour is never used
**MoSCoW:** Must. THE SYSTEM SHALL write each player-facing line with at most one accent palette tag besides the dim structure tag,
and SHALL NOT use a red colour in any bundle value, in line with the course's own red finish. A display name's colour is not an
accent for the one-accent rule, so a line that names a map or cup may carry the name's colour and one accent tag. A display name
is not a bundle value, so it may use any colour.

#### Scenario: A line with two accents
- **WHEN** a bundle value uses two accent tags in one line, neither of them a display name
- **THEN** the style test fails and names the key

#### Scenario: A line that names a map
- **WHEN** the finish line names a map in its colour and carries one accent tag for the medal word
- **THEN** the style test accepts the line, because the name's colour is not counted

#### Scenario: A display name in red
- **WHEN** a map's display name is red
- **THEN** the file loads, because the bundle rule does not apply to display names
