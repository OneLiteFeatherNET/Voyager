# Spec Delta

## Purpose

When a cup ends, each racer is told their place in one large title with a sound that fits the place, fireworks mark the top
three, and the standings name the cup and its maps in colour. The celebration fits inside the results linger that already runs
before the hand-off to the hub (`WaitingRoom.RESULTS_LINGER_TICKS`, 200 ticks, 10 s). This change does not lengthen that linger.

## ADDED Requirements

### Requirement: Each racer is told their own place in a large title
**MoSCoW:** Must. WHEN a cup ends, THE SYSTEM SHALL show each racer who is online a title for their own place, translated in their
language: "Victory!" or "Sieg!" for first place, "2nd place" or "Platz 2", "3rd place" or "Platz 3", and "Place <n>" or "Platz <n>"
for every other place. The subtitle SHALL give the racer's points.

#### Scenario: The winner's title
- **WHEN** a German-speaking racer finishes first
- **THEN** the title is "Sieg!" and the subtitle gives their points

#### Scenario: Fourth place
- **WHEN** an English-speaking racer finishes fourth
- **THEN** the title is "Place 4"

#### Scenario: A racer who has left
- **WHEN** a racer disconnected before the cup ended
- **THEN** no title is sent to them and their place still appears in the standings as "(left)"

### Requirement: The title stays on screen for a readable time
**MoSCoW:** Must. THE SYSTEM SHALL keep each placement title on screen for at least four seconds and no more than six.

#### Scenario: Title duration
- **WHEN** a placement title is shown
- **THEN** it stays on screen for at least four seconds and no more than six

### Requirement: A sound fits the place
**MoSCoW:** Must. WHEN a cup ends, THE SYSTEM SHALL play to each racer a fanfare for first place, a shorter fanfare for second and third,
and a soft chime for every other place.

#### Scenario: The sound for first place
- **WHEN** a racer finishes first
- **THEN** the fanfare plays for that racer only

#### Scenario: The sound for fourth place
- **WHEN** a racer finishes fourth
- **THEN** only the soft chime plays for that racer

### Requirement: Fireworks mark the top three, and the winner's are the largest
**MoSCoW:** Must. WHEN a cup ends, THE SYSTEM SHALL burst fireworks at the position of each racer with a place of first, second
or third: five bursts for first place, two for second and two for third, within four seconds. No other place gets a burst.

#### Scenario: The winner's bursts
- **WHEN** the cup ends and a racer finishes first
- **THEN** five bursts appear at that racer's position within four seconds

#### Scenario: No burst for fourth place
- **WHEN** a racer finishes fourth
- **THEN** no burst is made for that racer

#### Scenario: The fallback
- **WHEN** the pinned Minestom API cannot set a firework explosion
- **THEN** the bursts are particles at the same positions and times, and the sound is unchanged

### Requirement: The celebration ends within the results linger
**MoSCoW:** Must. THE SYSTEM SHALL start the title, the sound and the bursts on the tick the cup ends, so that all of them finish before
the hand-off to the hub at the end of the results linger. The results linger SHALL stay at 200 ticks.

#### Scenario: Timing against the linger
- **WHEN** a cup ends
- **THEN** the last burst occurs within four seconds, the title is removed by six seconds, and the hand-off to the hub happens at ten seconds

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

### Requirement: Each standings line shows points, best time, rings hit and maps finished
**MoSCoW:** Must. THE SYSTEM SHALL write each racer's line with the place, the name, the points, the best time of the cup, the maps
finished, and the rings hit as "<passed>/<total>". `<passed>` is the number of rings the racer passed across the cup's maps, and
`<total>` is the number of rings of every map of the cup, whether or not the racer played it. A racer with no best time shows "—".
THE SYSTEM SHALL track the rings passed in the cup score.

#### Scenario: A racer's line
- **WHEN** a racer passed 58 of 70 rings across a two-map cup with 35 rings each, finished both maps, and has a best time of 1:44.3 and 365 points
- **THEN** the line shows the place, the name, "365 points", "1:44.3", the maps finished "2/2" and "58/70"

#### Scenario: A late joiner's total
- **WHEN** a racer joined at the second map and passed 30 of its 35 rings
- **THEN** the line shows "30/70", because the total counts every ring of the cup

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
