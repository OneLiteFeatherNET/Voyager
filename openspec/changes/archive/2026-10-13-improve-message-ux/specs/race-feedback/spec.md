# Spec Delta

## Purpose

Defines what a racer sees and hears at the moments a race turns: the start, a ring passed or missed, the map result and the
cup result. It fixes the timing and the channel of each moment, and it adds the one feedback signal the game lacks, the
missed ring.

## ADDED Requirements

### Requirement: The GO title stays on screen long enough to be read
**MoSCoW:** Should. WHEN a racer receives the GO title, THE SYSTEM SHALL hold it for at least 20 game ticks (1.0 s) before
its fade-out, so that it is on screen for at least 1.0 s in total.

#### Scenario: The GO title at the start of a map
- **WHEN** the countdown ends and the GO title is sent to a racer
- **THEN** the title is held for 20 ticks and fades in 0 ticks, and the subtitle "Jump, then deploy your elytra" is shown for the same span

### Requirement: Each sound means one thing, and no two meanings share a sound
**MoSCoW:** Must. THE SYSTEM SHALL play the countdown beat only for a countdown digit, the ring bell or the last-ring chime
only for a ring pass, and a different sound for a reset, a DNF and a missed ring.

#### Scenario: A reset does not sound like a countdown
- **WHEN** a racer is reset to a ring or to the start
- **THEN** the reset sound is not the countdown beat sample, at any pitch

#### Scenario: A DNF is quieter than a finished medal
- **WHEN** a map ends as DNF
- **THEN** the DNF sound's volume is below the volume of the medal sound for Diamond, Gold, Silver and Bronze

### Requirement: A missed ring is signalled once, in the action bar and by sound
**MoSCoW:** Should. WHEN a racer passes ring k+1 while ring k is still the next ring expected, THE SYSTEM SHALL report ring k
as missed exactly once, play the missed-ring sound once, and show `Ring <k> missed` in the action bar for 1.5 s, in the warn
colour, after which the flight HUD returns on its next refresh.

#### Scenario: Skipping one ring
- **WHEN** a racer passes ring 6 directly after ring 4
- **THEN** ring 5 is reported missed once, and the missed-ring sound and the line `Ring 5 missed` are sent once

#### Scenario: A normal run
- **WHEN** a racer passes rings 1 to 35 in order
- **THEN** no ring is reported missed and no missed-ring cue is sent

#### Scenario: The same ring is not reported twice
- **WHEN** a racer passes ring 6 again after a reset to ring 4
- **THEN** ring 5 is not reported missed a second time for the same run

### Requirement: The map result names the next goal in seconds, when one is reachable
**MoSCoW:** Should. WHEN a racer finishes a map with a medal that has a better medal above it, THE SYSTEM SHALL show in the
result subtitle the seconds the racer would need to save to reach the next better medal, with one decimal. WHEN no better
medal is reachable, THE SYSTEM SHALL omit the subtitle. WHEN the map ended as DNF, THE SYSTEM SHALL show the rings passed
out of the rings in the map, with no points and no next goal.

#### Scenario: A silver finish
- **WHEN** a racer finishes with a Silver medal 0.8 s slower than the Gold boundary
- **THEN** the subtitle names 0.8 s as the time to save for Gold

#### Scenario: A Diamond finish
- **WHEN** a racer finishes with Diamond
- **THEN** the subtitle is omitted

#### Scenario: A DNF
- **WHEN** a racer's map ends as DNF after 5 of 35 rings
- **THEN** the subtitle reads 5 of 35 rings, with no points and no next goal

### Requirement: The map result lists its points in chat only
**MoSCoW:** Should. THE SYSTEM SHALL list the points breakdown of a map result in chat only. THE SYSTEM SHALL show the medal
word as the title and the next goal, or nothing, as the subtitle, and neither SHALL repeat a point figure.

#### Scenario: A finished map
- **WHEN** a racer finishes a map
- **THEN** chat holds the breakdown line once, the title shows the medal word, and the subtitle shows the next goal in seconds

### Requirement: Joins in a short burst are told as one line
**MoSCoW:** Should. WHEN racers join the waiting room within 60 game ticks of each other, THE SYSTEM SHALL send the racers
already online one line for the burst, counting the joins and naming the count against the maximum. A join that is the first
of its burst SHALL be sent at once, without waiting for the window to close.

#### Scenario: One join
- **WHEN** a single racer joins an empty waiting room
- **THEN** the racers online receive the join line for that racer at once

#### Scenario: Three joins within the window
- **WHEN** three racers join within 60 game ticks, with eight of twelve online after the third
- **THEN** the racers online receive one line that reads three racers joined and states eight of twelve

#### Scenario: A join after the window
- **WHEN** a racer joins 61 game ticks after the previous join
- **THEN** a new burst starts and its line is sent at once

### Requirement: The setup wand instruction is two lines, placement first
**MoSCoW:** Should. THE SYSTEM SHALL send the setup wand instruction as two chat lines in order: the placing instruction
(right-click), then the removing instruction (left-click).

#### Scenario: Receiving the wand
- **WHEN** a builder is given the setup wand
- **THEN** the placing line is sent first and the removing line second, each readable without scrolling
