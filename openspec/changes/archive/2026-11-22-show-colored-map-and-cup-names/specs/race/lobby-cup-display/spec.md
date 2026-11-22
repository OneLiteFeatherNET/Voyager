# Spec Delta

## Purpose

The main specification says the sidebar shows twelve map lines. The code (`LobbyBoard.MAX_MAP_LINES`) shows ten, and ten keeps
the sidebar under the fifteen-line cap. This delta corrects the requirement to ten, and removes the footer line, which the
title already makes redundant: the sidebar ends with its status line.

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

## ADDED Requirements

### Requirement: The sidebar ends with its status line and has no footer
**MoSCoW:** Must. THE SYSTEM SHALL NOT show a footer line on the sidebar. THE SYSTEM SHALL end the sidebar with its status line, and
no blank line SHALL follow it. The blank line between the map lines and the status line stays. The sidebar title already names the
cup, so no other line names the game.

#### Scenario: The last line of the sidebar is the status
- **WHEN** a racer waits for a cup with three maps
- **THEN** the sidebar's last line is the status line, "waiting" or "starts in <n> s", and no line reads "Elytra Race" or "Elytra-Rennen"

#### Scenario: No trailing blank line
- **WHEN** the sidebar is shown for a cup of any number of maps
- **THEN** no empty line follows the status line

#### Scenario: The sidebar line count
- **WHEN** the cup has twelve maps
- **THEN** the sidebar has at most fourteen lines, being the racers line, a blank, ten map lines, a blank and the status line, and the title is not one of them
