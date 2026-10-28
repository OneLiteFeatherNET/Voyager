# Spec Delta

## Purpose

Every place that shows a map or a cup shows its coloured display name and, for a map, its authors. The layout of the boss bar
and the lobby is unchanged; only the name and the authors are added.

## ADDED Requirements

### Requirement: The map announce line names the map in its colour
**MoSCoW:** Must. WHEN a map starts, THE SYSTEM SHALL write the map announce line in chat with the map's coloured display name in
place of its key, for example "» Map 1/1 · Blue & Red — 35 rings · target 1:00".

#### Scenario: The announce line of the shipped map
- **WHEN** the map `elytrarace:blue_and_red` starts as map 1 of 1
- **THEN** the chat line names "Blue & Red" in the map's colour, and the line contains no `elytraraceblueandred` and no `elytrarace:`

### Requirement: The finish line and the map result name the map in its colour
**MoSCoW:** Must. THE SYSTEM SHALL write the finish line "Finished <map> in <time>" and the map result line with the coloured
display name of the map. The medal word of the result is written in mixed case in its translation, never as an enumeration
name, so the result reads "Finish" in English and "Ziel" in German, not "FINISH".

#### Scenario: The finish line
- **WHEN** a racer finishes `elytrarace:blue_and_red` in 1:44.3
- **THEN** the chat reads "Finished Blue & Red in 1:44.3", with the name coloured

#### Scenario: The result line has no raw identifier
- **WHEN** a racer finishes the map and receives the result line
- **THEN** the line contains the map's display name, the ring points, the medal points, the medal word and the total, and no key or enumeration name

### Requirement: Each map starts with an intro title that names the map and its authors
**MoSCoW:** Must. WHEN a map's countdown begins, THE SYSTEM SHALL show each racer a title with the coloured display name of the map,
and a subtitle "by <authors>" in English or "von <Autoren>" in German, with the authors joined by ", ". IF the map has no authors,
THEN THE SYSTEM SHALL show the title without a subtitle. The title replaces the countdown subtitle "map · rings · target", whose
figures are already in the announce line.

#### Scenario: A map with two authors
- **WHEN** a map with the authors "Alice" and "Bob" starts for a German-speaking racer
- **THEN** the title is the map's name and the subtitle is "von Alice, Bob"

#### Scenario: A map without authors
- **WHEN** a map with no authors starts
- **THEN** the title is the map's name and no subtitle is shown

### Requirement: The boss bar keeps its shipped layout and names the map in its colour
**MoSCoW:** Must. THE SYSTEM SHALL keep the layout of the race boss bar as shipped in `polish-race-hud` and SHALL change only the
map name in it to the coloured display name.

#### Scenario: The boss bar title
- **WHEN** a racer is on map 1 of 1 of `elytrarace:blue_and_red` with the medal band "Gold" and 12 seconds left
- **THEN** the boss bar reads "Map 1/1 · Blue & Red · Gold · 12 s left", with the name in its colour and the rest as before

### Requirement: The lobby sidebar names the cup and each map, with the authors on the map line
**MoSCoW:** Must. WHILE a racer waits, THE SYSTEM SHALL title the sidebar with the coloured cup display name and SHALL write each map
line as its place, the coloured display name and, when the map has authors, " · by <first author>" with " +<n>" for the others.
THE SYSTEM SHALL keep the sidebar's line count at or below fifteen. A map line whose text would be longer than forty visible
characters SHALL drop the author list and keep the name.

#### Scenario: A cup of one map
- **WHEN** the room waits for `elytrarace:alpha_cup` with the map `elytrarace:blue_and_red`, which has the authors "Alice" and "Bob"
- **THEN** the sidebar title is "Alpha Cup" in its colour, and its map line reads "1. Blue & Red · by Alice +1"

#### Scenario: The sidebar line count
- **WHEN** the cup has twelve maps
- **THEN** the sidebar shows at most ten map lines and at most fifteen lines in total

### Requirement: The lobby hologram names each map in its colour
**MoSCoW:** Should. THE SYSTEM SHALL show each map name on the lobby hologram in the map's colour, without authors.

#### Scenario: Hologram lines
- **WHEN** the hologram lists the maps of `elytrarace:alpha_cup`
- **THEN** each line is the map's display name in its colour

### Requirement: The tutorial book names the racer's cup and its maps with their authors
**MoSCoW:** Must. WHEN a racer opens the tutorial, THE SYSTEM SHALL add a sixth page that names the cup in its colour, lists its maps in
play order with their coloured names and authors, and shows the cup's authors. THE SYSTEM SHALL keep the other five pages as they are.

#### Scenario: The cup page
- **WHEN** a racer waiting for `elytrarace:alpha_cup` turns to page 6
- **THEN** the page names "Alpha Cup", the map "Blue & Red" with its authors, and the cup's authors

#### Scenario: A cup with no authors
- **WHEN** the cup has no authors
- **THEN** the cup page shows its maps and no author line for the cup
