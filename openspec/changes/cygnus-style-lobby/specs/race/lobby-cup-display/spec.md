# Spec Delta

## Purpose

The lobby shows the cup that waiting racers are about to play, by its display name and its maps in play order. The sidebar
is per racer and tidy; the hologram is one shared entity and shows names only. The boss bar no longer takes part.

## MODIFIED Requirements

### Requirement: One board builds the cup text for every lobby display
**MoSCoW:** Must. THE SYSTEM SHALL build the cup name and the ordered map lines from one board, and THE SYSTEM SHALL show the
same cup name and map order on the sidebar and on the hologram. Each map line is the map's display name from
`catalog/display-names`, and THE SYSTEM SHALL NOT show a map id.

#### Scenario: The three displays agree
- **WHEN** the room is waiting for the cup "Grand Tour" with the maps "Alpha", "Bravo" and "Charlie"
- **THEN** the sidebar and the hologram each name "Grand Tour", and the sidebar and the hologram list "Alpha", "Bravo" and "Charlie" in that order; the lobby boss bar is removed, so it is no longer one of the displays

### Requirement: The sidebar lists the cup while racers wait
**MoSCoW:** Must. WHILE a racer waits, that is in the situation WAITING, COUNTDOWN or COMMITTED, THE SYSTEM SHALL show that racer a
sidebar with the cup's display name as its title and the cup's maps in play order as its map lines. THE SYSTEM SHALL NOT show
the sidebar while a cup is racing.

#### Scenario: Sidebar while waiting
- **WHEN** a racer waits in the lobby with no cup running
- **THEN** the racer's sidebar shows the cup's display name and its maps in play order

#### Scenario: Sidebar hidden during a race
- **WHEN** the cup starts and the racers enter the first map
- **THEN** no racer has a lobby sidebar, and the race's own displays are the only ones shown

### Requirement: The sidebar holds a fixed number of map lines
**MoSCoW:** Must. THE SYSTEM SHALL show at most ten map lines on the sidebar. IF the cup has more than ten maps, THEN THE SYSTEM
SHALL show the first nine and one line that states how many maps follow. The sidebar's other lines (racers, status and footer)
SHALL NOT push the sidebar past the fifteen lines that the client shows.

#### Scenario: A cup with more maps than the sidebar holds
- **WHEN** the cup "Long Tour" has fifteen maps
- **THEN** the sidebar shows the first nine maps and the line "+6 more"

#### Scenario: A cup with twelve maps or fewer
- **WHEN** the cup has five maps
- **THEN** the sidebar shows all five and no "more" line

### Requirement: The lobby shows the cup that is currently loaded
**MoSCoW:** Must. THE SYSTEM SHALL show the cup the catalogue holder currently holds, which is the cup a racer waits for. A
pending reload SHALL NOT change what the lobby shows until a round starts and promotes it; from that round on the lobby shows
the promoted cup. A countdown SHALL keep the cup it began with.

#### Scenario: A reload waits for the next round
- **WHEN** a reload changes the cup "Grand Tour" to "Grand Tour II" while the room is waiting and no round has started
- **THEN** the lobby still displays "Grand Tour", and the next round that starts plays and displays "Grand Tour II"

#### Scenario: The countdown keeps its cup
- **WHEN** a countdown for "Grand Tour" is running and a reload is applied
- **THEN** the lobby displays keep "Grand Tour" until the round starts

### Requirement: A hologram shows the cup at a fixed position in the lobby world
**MoSCoW:** Should. WHERE the setting `VOYAGER_LOBBY_HOLOGRAM` names a position, THE SYSTEM SHALL keep one text display entity at that
position in the lobby world, showing the cup's display name and the display names of its maps, and SHALL update its text when the
cup shown changes. THE SYSTEM SHALL spawn it once at boot, after the lobby world is loaded, and SHALL NOT save it with the lobby
world. The hologram SHALL show no translated line: it SHALL NOT show the "more maps" line, because it is one shared entity and
cannot follow each racer's language.

#### Scenario: Hologram at the configured position
- **WHEN** the server boots with `VOYAGER_LOBBY_HOLOGRAM=0,70,0` and a lobby world
- **THEN** one text display stands at 0, 70, 0 in the lobby world and shows the cup's display name and the display names of its first maps

#### Scenario: Hologram text follows a reload
- **WHEN** the cup shown changes while the room is waiting
- **THEN** the hologram's text shows the new cup's display name and maps

#### Scenario: No hologram without a position
- **WHEN** `VOYAGER_LOBBY_HOLOGRAM` is not set
- **THEN** no text display is spawned and no warning is logged for it

## ADDED Requirements

### Requirement: The sidebar shows the racers, the status and a footer
**MoSCoW:** Must. THE SYSTEM SHALL show on each racer's lobby sidebar, below the map lines, the number of racers online out of the
maximum, a status line, and a footer line, each in the racer's language. The status line SHALL read "waiting" while the minimum
is not met and "starts in N s" while the countdown runs. THE SYSTEM SHALL NOT show a score number on any sidebar line.

#### Scenario: Waiting for racers
- **WHEN** three racers of a maximum of sixteen are online and the minimum is not met
- **THEN** the sidebar shows "Racers 3/16" in the racer's language, the status "waiting", and the footer

#### Scenario: Countdown running
- **WHEN** the minimum is met and twelve seconds of the lobby remain
- **THEN** the sidebar's status line reads the seconds to the start, twelve

#### Scenario: No score numbers
- **WHEN** the sidebar is shown
- **THEN** no line shows a number in the score column, so no map line reads as a rank

### Requirement: The lobby sidebar is one sidebar per racer
**MoSCoW:** Must. THE SYSTEM SHALL build each racer's lobby sidebar for that racer alone, so that its text is rendered in that
racer's language, and SHALL NOT share one sidebar object between racers.

#### Scenario: Two racers in two languages
- **WHEN** a `de_DE` racer and an `en_US` racer wait in the same room
- **THEN** each sees the status line and the racers line in its own language, and the map names are the same for both
