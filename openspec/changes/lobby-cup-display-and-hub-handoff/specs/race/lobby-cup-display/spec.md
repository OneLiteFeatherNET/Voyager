# Spec Delta

## Purpose

The lobby shows the cup that waiting racers are about to play: its name and its maps in play order, on a sidebar, on a hologram
at a fixed position, and on the boss bar. Racers see what comes next without flying, and the three displays always agree.

## ADDED Requirements

### Requirement: One board builds the cup text for every lobby display
**MoSCoW:** Must. THE SYSTEM SHALL build the cup name and the ordered map lines from one board, and THE SYSTEM SHALL show the
same cup name and map order on the sidebar, on the hologram and on the boss bar.

#### Scenario: The three displays agree
- **WHEN** the room is waiting for the cup "Grand Tour" with the maps "Alpha", "Bravo" and "Charlie"
- **THEN** the sidebar, the hologram and the boss bar each name "Grand Tour", and the sidebar and the hologram list "Alpha", "Bravo" and "Charlie" in that order

### Requirement: The sidebar lists the cup while racers wait
**MoSCoW:** Must. WHILE a racer waits, that is in the situation WAITING, COUNTDOWN or COMMITTED, THE SYSTEM SHALL show that racer a
sidebar with the cup's name as its title and the cup's maps in play order as its lines. THE SYSTEM SHALL NOT show the sidebar
while a cup is racing.

#### Scenario: Sidebar while waiting
- **WHEN** a racer waits in the lobby with no cup running
- **THEN** the racer's sidebar shows the cup's name and its maps in play order

#### Scenario: Sidebar hidden during a race
- **WHEN** the cup starts and the racers enter the first map
- **THEN** no racer has a lobby sidebar, and the race's own displays are the only ones shown

### Requirement: The sidebar holds a fixed number of map lines
**MoSCoW:** Must. THE SYSTEM SHALL show at most twelve map lines on the sidebar. IF the cup has more than twelve maps, THEN THE
SYSTEM SHALL show the first twelve and one line that states how many maps follow.

#### Scenario: A cup with more maps than the sidebar holds
- **WHEN** the cup "Long Tour" has fifteen maps
- **THEN** the sidebar shows the first twelve maps and the line "+3 more"

#### Scenario: A cup with twelve maps or fewer
- **WHEN** the cup has five maps
- **THEN** the sidebar shows all five and no "more" line

### Requirement: The cup shown while waiting is the cup the next round plays
**MoSCoW:** Must. WHILE the room is WAITING, THE SYSTEM SHALL show the cup of the pending catalogue when a reload is pending, and
the current cup otherwise. WHILE a countdown or a committed start runs, THE SYSTEM SHALL show the cup that round has pinned.

#### Scenario: A reloaded cup shows before the next round
- **WHEN** a reload changes the cup "Grand Tour" to "Grand Tour II" while the room is waiting
- **THEN** the lobby displays name "Grand Tour II" before the next round begins, and the round that starts plays "Grand Tour II"

#### Scenario: The countdown keeps its cup
- **WHEN** a countdown for "Grand Tour" is running and a reload is applied
- **THEN** the lobby displays keep "Grand Tour" until the round ends

### Requirement: A hologram shows the cup at a fixed position in the lobby world
**MoSCoW:** Should. WHERE the setting `VOYAGER_LOBBY_HOLOGRAM` names a position, THE SYSTEM SHALL keep one text display entity at that
position in the lobby world, showing the cup name and the map lines, and SHALL update its text when the cup shown changes. THE
SYSTEM SHALL spawn it once at boot, after the lobby world is loaded, and SHALL NOT save it with the lobby world.

#### Scenario: Hologram at the configured position
- **WHEN** the server boots with `VOYAGER_LOBBY_HOLOGRAM=0,70,0` and a lobby world
- **THEN** one text display stands at 0, 70, 0 in the lobby world and shows the cup name and its maps

#### Scenario: Hologram text follows a reload
- **WHEN** the cup shown changes while the room is waiting
- **THEN** the hologram's text shows the new cup's name and maps

#### Scenario: No hologram without a position
- **WHEN** `VOYAGER_LOBBY_HOLOGRAM` is not set
- **THEN** no text display is spawned and no warning is logged for it
