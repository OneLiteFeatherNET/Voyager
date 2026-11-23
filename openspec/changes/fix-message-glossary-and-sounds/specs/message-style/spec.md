## ADDED Requirements

### Requirement: The fitness suite checks the glossary in every race line
**Priority:** MoSCoW Must. THE SYSTEM SHALL check, in the fitness test suite, that no race line in any bundle uses `player` or
`players` for a person taking part in a race. A line that uses the vanilla join phrasing SHALL be listed by key in the test, with
the reason, and SHALL be the only exception.

#### Scenario: A race line naming a person as a player
- **WHEN** a race line in a bundle contains `player` or `players` for a person in a race and its key is not in the exception list
- **THEN** the fitness test fails and names the key and the bundle

#### Scenario: The lobby waiting line
- **WHEN** the fitness suite runs on the bundles after this change
- **THEN** the lobby waiting lines `voyager.lobby.actionbar.waiting.one` and `voyager.lobby.actionbar.waiting.other` use `racer` or `racers`, and the test passes

#### Scenario: The German bundle
- **WHEN** the German bundle is checked
- **THEN** the same race lines use the German word for racer and the check reports no `Spieler` in them
