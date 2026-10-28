## MODIFIED Requirements

### Requirement: The map result lists its points in chat only
**MoSCoW:** Should. THE SYSTEM SHALL list the points breakdown of a map result in chat only, and SHALL list the place line of
that result in chat once, after the breakdown. THE SYSTEM SHALL show the medal word as the title and the next goal, or nothing,
as the subtitle, and neither SHALL repeat a point figure.

#### Scenario: A finished map
- **WHEN** a racer finishes a map
- **THEN** chat holds the breakdown line once and the place line once, the title shows the medal word, and the subtitle shows
  the next goal in seconds
