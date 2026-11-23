## MODIFIED Requirements

### Requirement: Nothing in the end screen is added beyond the named elements
**MoSCoW:** Must. THE SYSTEM SHALL NOT add a play-again item, a longer celebration pause before the hand-off, or a per-racer breakdown
shown on hover to the end screen. THE SYSTEM SHALL add no element to the end screen's titles, subtitles or boss bar beyond those it
names; the one chat line of the next goal, defined below, is chat and is not an end-screen element.

#### Scenario: No play-again item
- **WHEN** a racer is handed off after the cup
- **THEN** no play-again item or button is shown to them

#### Scenario: The next goal is chat, not a title
- **WHEN** a racer is handed off after the cup and the next-goal line is sent
- **THEN** the line is in chat, and the title and subtitle are unchanged

## ADDED Requirements

### Requirement: Each human racer is told the next medal they could reach on their weakest map
**MoSCoW:** Should. WHEN the cup's final standings are announced, THE SYSTEM SHALL send each human racer one chat line after the
standings naming the map on which they hold the lowest medal, and the seconds they would need to save to reach the next medal on
that map, where the seconds are rounded up to a whole second.

#### Scenario: A racer with a weakest map in the rotation
- **WHEN** a racer's lowest medal is Silver on a map that is in the armed rotation, and the next medal, Gold, needs 4.2 seconds saved
- **THEN** the racer receives one chat line naming that map and "5 s" for Gold, after the standings

#### Scenario: A racer whose weakest map is not in the rotation
- **WHEN** a racer's weakest map is not in the armed rotation
- **THEN** no next-goal line is sent to that racer

#### Scenario: A racer with a diamond on every map
- **WHEN** a racer holds a Diamond on every map of the cup
- **THEN** no next-goal line is sent to that racer

#### Scenario: A map the racer did not finish is not chosen
- **WHEN** a racer did not finish one map of the cup and finished the others
- **THEN** the weakest map is chosen among the maps the racer finished, and the did-not-finish map is not named

### Requirement: The next-goal line names one map and one medal, and has no action
**MoSCoW:** Must. THE SYSTEM SHALL send at most one next-goal line per racer, with no button, no click action and no timer.

#### Scenario: One line per racer
- **WHEN** the cup's final standings are announced
- **THEN** each human racer receives at most one next-goal line
