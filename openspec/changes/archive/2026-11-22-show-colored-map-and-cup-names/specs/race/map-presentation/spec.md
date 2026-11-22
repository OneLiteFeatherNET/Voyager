# Spec Delta

## Purpose

Every place that shows a map or a cup shows its coloured display name and, for a map, its authors. The layout of the boss bar
and the lobby is unchanged; only the name and the authors are added, and the stats commands show the coloured name too.

## ADDED Requirements

### Requirement: The map announce line names the map in its colour
**MoSCoW:** Must. WHEN a map starts, THE SYSTEM SHALL write the map announce line in chat with the map's coloured display name in
place of its key, for example "» Map 1/1 · Blue & Red — 35 rings · target 1:00".

#### Scenario: The announce line of the shipped map
- **WHEN** the map `elytrarace:blue_and_red` starts as map 1 of 1
- **THEN** the chat line names "Blue & Red" in the map's colour, and the line contains no `elytraraceblueandred` and no `elytrarace:`

### Requirement: The finish line and the map result name the map in its colour
**MoSCoW:** Must. THE SYSTEM SHALL write the finish line "Finished <map> in <time>" and the map result line with the coloured
display name of the map. The medal word is written in sentence case in its translation, "Finish" in English and "Ziel" in German,
never in capitals and never as an enumeration name.

#### Scenario: The finish line
- **WHEN** a racer finishes `elytrarace:blue_and_red` in 1:44.3
- **THEN** the chat reads "Finished Blue & Red in 1:44.3", with the name coloured

#### Scenario: The result line has no raw identifier
- **WHEN** a racer finishes the map and receives the result line
- **THEN** the line contains the map's display name, the ring points, the medal points, the medal word "Finish" or "Ziel", and the total, and no key or enumeration name

#### Scenario: The name colour is not an accent
- **WHEN** the finish line is checked against the one-accent rule
- **THEN** the colour of the map name is not counted, and the medal word is the one accent

### Requirement: Each map starts with an intro title that names the map and its authors
**MoSCoW:** Must. WHEN a map's countdown begins, THE SYSTEM SHALL show each racer a title with the coloured display name of the map,
timed 10 ticks fade in, 60 ticks stay and 20 ticks fade out, with a subtitle of the authors. The intro is shown before the countdown
digits.

#### Scenario: A map with two authors
- **WHEN** a map with the authors "Alice" and "Bob" starts for a German-speaking racer
- **THEN** the title is the map's name, the subtitle is "von Alice, Bob", and the title times are 10, 60 and 20 ticks

#### Scenario: A map without authors
- **WHEN** a map with no authors starts
- **THEN** the title is the map's name and no subtitle is shown

#### Scenario: The map of the shipped cup
- **WHEN** `elytrarace:blue_and_red` starts
- **THEN** the subtitle is "by TheMeinerLP"

### Requirement: The boss bar keeps its shipped layout and names the map in its colour
**MoSCoW:** Must. THE SYSTEM SHALL keep the layout of the race boss bar as shipped in `polish-race-hud` and SHALL change only the
map name in it to the coloured display name.

#### Scenario: The boss bar title
- **WHEN** a racer is on map 1 of 1 of `elytrarace:blue_and_red` with the medal band "Gold" and 12 seconds left
- **THEN** the boss bar reads "Map 1/1 · Blue & Red · Gold · 12 s left", with the name in its colour and the rest as before

### Requirement: The lobby sidebar names the cup and each map, with the authors on the map line
**MoSCoW:** Must. WHILE a racer waits, THE SYSTEM SHALL title the sidebar with the coloured cup display name and write each map line
with its place, its coloured display name and its first author. The sidebar has no footer line and stays within fourteen lines.

#### Scenario: A cup of one map
- **WHEN** the room waits for `elytrarace:alpha_cup` with the map `elytrarace:blue_and_red`, which has the authors "TheMeinerLP"
- **THEN** the sidebar title is "Alpha Cup" in its colour, and its map line reads "1. Blue & Red · by TheMeinerLP"

#### Scenario: Further authors are counted
- **WHEN** a map has the authors "TheMeinerLP" and "Seelenretterin"
- **THEN** its map line reads "· by TheMeinerLP +1"

#### Scenario: A long line drops its authors
- **WHEN** a map line with its authors would be longer than forty visible characters
- **THEN** the author list is dropped and the name is kept whole

#### Scenario: The sidebar line count
- **WHEN** the cup has twelve maps
- **THEN** the sidebar shows at most ten map lines and at most fourteen lines in total, ending with the status line

### Requirement: The lobby hologram names each map in its colour
**MoSCoW:** Should. THE SYSTEM SHALL show each map name on the lobby hologram in the map's colour, without authors.

#### Scenario: Hologram lines
- **WHEN** the hologram lists the maps of `elytrarace:alpha_cup`
- **THEN** each line is the map's display name in its colour

### Requirement: The tutorial book names the racer's cup and its maps with their authors on a new page
**MoSCoW:** Must. WHEN a racer opens the tutorial, THE SYSTEM SHALL add a sixth page that names the cup in its colour, lists its maps in
play order with their coloured names and authors, and shows the cup's authors. THE SYSTEM SHALL keep the other five pages as they are.

#### Scenario: The cup page
- **WHEN** a racer waiting for `elytrarace:alpha_cup` turns to page 6
- **THEN** the page names "Alpha Cup", the map "Blue & Red" with its authors, and the cup's authors

#### Scenario: A cup with no authors
- **WHEN** the cup has no authors
- **THEN** the cup page shows its maps and no author line for the cup

### Requirement: The stats commands name the map in its colour
**MoSCoW:** Should. WHEN a racer runs `/stats` or `/leaderboard`, THE SYSTEM SHALL name each map in the reply by its coloured display
name, and SHALL NOT print the stored key.

#### Scenario: A leaderboard reply
- **WHEN** a racer runs `/leaderboard blue_and_red`
- **THEN** the reply header names the map "Blue & Red" in its colour and no line shows `elytrarace:`
