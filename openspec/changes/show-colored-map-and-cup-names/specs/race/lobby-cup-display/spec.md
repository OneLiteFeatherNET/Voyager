# Spec Delta

## Purpose

The main specification says the sidebar shows twelve map lines. The code (`LobbyBoard.MAX_MAP_LINES`) shows ten, and ten is
the count that keeps the sidebar at fourteen lines under the fifteen-line cap, with the title, the racers line, the status line
and the footer. This delta corrects the requirement to ten.

## MODIFIED Requirements

### Requirement: The sidebar holds a fixed number of map lines
**MoSCoW:** Must. THE SYSTEM SHALL show at most ten map lines on the sidebar. IF the cup has more than ten maps, THEN THE SYSTEM SHALL
show the first nine maps and one line that states how many maps follow, so the ten lines include that line.

#### Scenario: A cup with more maps than the sidebar holds
- **WHEN** the cup "Long Tour" has fifteen maps
- **THEN** the sidebar shows the first nine maps and the line "+6 more"

#### Scenario: A cup with twelve maps or fewer
- **WHEN** the cup has ten maps
- **THEN** the sidebar shows all ten and no "more" line
