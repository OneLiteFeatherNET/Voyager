# Spec Delta

## Purpose

When a cup ends, each racer is told their place in one large title with fireworks and a sound that fits the place, and the
standings name the cup and its maps in colour. The celebration fits inside the results linger that already runs before the
hand-off to the hub (`WaitingRoom.RESULTS_LINGER_TICKS`, 200 ticks, 10 s). This change does not lengthen that linger.

## ADDED Requirements

### Requirement: Each racer is told their own place in a large title with a sound
**MoSCoW:** Must. WHEN a cup ends, THE SYSTEM SHALL show each racer who is online a title for their own place, translated in their
language: "Victory!" or "Sieg!" for first place, "2nd place" or "Platz 2", "3rd place" or "Platz 3", and "Place <n>" or "Platz <n>"
for every other place. The subtitle SHALL give the racer's points.

#### Scenario: The title stays long enough to read
- **WHEN** a placement title is shown
- **THEN** it stays on screen for at least four seconds and no more than six

#### Scenario: The sound fits the place
- **WHEN** a cup ends
- **THEN** a fanfare plays for first place, a shorter one for second and third, and a soft chime for every other place

#### Scenario: The winner's title
- **WHEN** a German-speaking racer finishes first
- **THEN** the title is "Sieg!" and the subtitle gives their points

#### Scenario: Fourth place
- **WHEN** an English-speaking racer finishes fourth
- **THEN** the title is "Place 4" and only the soft chime plays

#### Scenario: A racer who has left
- **WHEN** a racer disconnected before the cup ended
- **THEN** no title is sent to them and their place still appears in the standings as "(left)"

### Requirement: The fireworks mark the place, and the winner's are the largest
**MoSCoW:** Must. WHEN a cup ends, THE SYSTEM SHALL launch fireworks at the position of each racer with a place of first, second or
third, with five bursts for first place, two for second and two for third, spread over no more than four seconds. No fireworks SHALL
launch for any other place. Fireworks SHALL be visible to every racer in range, and the sound SHALL play for the racer only.

#### Scenario: The winner's fireworks
- **WHEN** the cup ends and a racer finishes first
- **THEN** five bursts launch at that racer's position within four seconds

#### Scenario: No fireworks for fourth place
- **WHEN** a racer finishes fourth
- **THEN** no firework is launched for that racer

### Requirement: The celebration ends within the results linger
**MoSCoW:** Must. THE SYSTEM SHALL start the title, the sound and the fireworks on the tick the cup ends, so that all of them finish
before the hand-off to the hub at the end of the results linger. The results linger SHALL stay at 200 ticks.

#### Scenario: Timing against the linger
- **WHEN** a cup ends
- **THEN** the last firework burst occurs within four seconds, the title is removed by six seconds, and the hand-off to the hub happens at ten seconds

### Requirement: The standings are coloured by place and name the cup and its maps
**MoSCoW:** Must. THE SYSTEM SHALL write the standings heading with the coloured cup display name, and a line that lists the cup's maps
in play order with their coloured display names. THE SYSTEM SHALL colour the place of the first, second and third racers in gold, silver
and bronze, and write every other place without colour.

#### Scenario: The heading and the map line
- **WHEN** `elytrarace:alpha_cup` ends
- **THEN** the heading "Alpha Cup" is in its colour, and the line "Maps: Blue & Red" names each map in its colour

#### Scenario: Medal colours by place
- **WHEN** the standings have three racers
- **THEN** the first place is gold, the second silver and the third bronze

### Requirement: Each standings line shows points, best time and rings hit
**MoSCoW:** Must. THE SYSTEM SHALL write each racer's line with the place, the name, the points, the best time of the cup, and the rings
hit as "<passed>/<total>", where `<total>` is the number of rings of every map of the cup and `<passed>` is the number of rings the racer
passed across the cup's maps. A racer with no best time shows "—". The line SHALL no longer show the count of maps finished. THE SYSTEM
SHALL track the rings hit in the cup score.

#### Scenario: A racer's line
- **WHEN** a racer passed 58 of 70 rings across a two-map cup with 35 rings each and has a best time of 1:44.3 and 365 points
- **THEN** the line shows the place, the name, "365 points", "1:44.3" and "58/70"

#### Scenario: A racer with no best time
- **WHEN** a racer finished no map
- **THEN** the best time shows "—"

### Requirement: The viewer's own standings line is highlighted
**MoSCoW:** Must. WHEN the standings are shown to a racer, THE SYSTEM SHALL highlight that racer's own line, and SHALL NOT change the other
lines. The highlight SHALL be bold and an arrow marker, and SHALL NOT add a colour to the line beyond its place colour.

#### Scenario: The viewer's line
- **WHEN** a racer who placed fourth reads the standings
- **THEN** their line is bold and has the marker, and the other lines are not bold

### Requirement: Nothing in the end screen is added beyond the named elements
**MoSCoW:** Must. THE SYSTEM SHALL NOT add a play-again item, a longer celebration pause before the hand-off, or a per-racer breakdown
shown on hover to the end screen.

#### Scenario: No play-again item
- **WHEN** a racer is handed off after the cup
- **THEN** no play-again item or button is shown to them
