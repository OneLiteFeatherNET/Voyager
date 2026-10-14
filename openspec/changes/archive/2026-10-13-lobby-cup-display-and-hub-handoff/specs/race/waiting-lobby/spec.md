# Delta for race/waiting-lobby

Depends on `add-lobby-world` (its delta is not archived yet) and on the main spec as it stands. Requirement and scenario names are
the ones in those specs. Requirements not listed here keep their current text.

## REMOVED Requirements

### Requirement: A finished cup returns the room to waiting
**Reason**: The owner decided on 2026-10-12 that every racer on the server goes to the network hub after a cup ends (ADR-0033), so
the lobby world does not get them back.
**Migration**: Replaced by "A finished cup hands every racer to the network hub" below.

## MODIFIED Requirements

### Requirement: Waiting racers are told how many are online and how many are needed
**MoSCoW:** Must. WHILE the minimum is not met, THE SYSTEM SHALL show every waiting racer, on the boss bar described in
"The room shows the count and the countdown on a boss bar", the name of the cup they wait for, the number of racers online and the
number needed to start. THE SYSTEM SHALL NOT use the action bar for this line.

#### Scenario: Count shown while waiting
- **WHEN** one racer of a minimum of two is online and waiting for the cup "Grand Tour"
- **THEN** the racer's boss bar states the cup "Grand Tour" and that one racer is online and that two are needed, and the action bar stays empty

### Requirement: The room shows the count and the countdown on a boss bar
**MoSCoW:** Must. WHILE a racer is in the lobby world, THE SYSTEM SHALL show that racer a boss bar stating the name of the cup
they wait for, and the waiting count and the maximum, or the cup's name and the seconds left of the start countdown. Where no lobby
world is configured, THE SYSTEM SHALL show the same bar to a waiting racer in the first map until the start is committed. THE SYSTEM
SHALL update the boss bar when its text or its progress changes, and SHALL hide it when the racer leaves the lobby world, or, without
a lobby, when the start is committed.

#### Scenario: The boss bar shows the waiting count
- **WHEN** one racer of a minimum of two is online and waiting for the cup "Grand Tour"
- **THEN** the racer's boss bar reads "Grand Tour" and that one of two racers is online, and the bar stays until the racer leaves the lobby world

#### Scenario: The boss bar shows the countdown
- **WHEN** the minimum is met, ten seconds of the lobby remain, and the cup is "Grand Tour"
- **THEN** the racer's boss bar reads "Grand Tour" and ten seconds to the start, and its progress is the fraction of the lobby that remains

#### Scenario: The boss bar leaves with the racer
- **WHEN** the first map is entered and a racer moves out of the lobby world
- **THEN** the lobby boss bar is hidden for that racer, and the race boss bar is the only one shown

#### Scenario: The fallback boss bar shows the count and the countdown in the first map
- **WHEN** no lobby world is configured and a racer waits at the first map's spawn with one racer of a minimum of two online, for the cup "Grand Tour"
- **THEN** the racer's boss bar reads "Grand Tour" and that one of two racers is online, and when the minimum is met the same bar reads the seconds to the start
- **AND** the bar is hidden for that racer once the start is committed, so the race boss bar is the only one shown from then on

## ADDED Requirements

### Requirement: A finished cup hands every racer to the network hub
**MoSCoW:** Must. WHEN a cup has finished and its results have lingered for `RESULTS_LINGER` (200 ticks, 10 s at 20 TPS) after the
cup result is announced, THE SYSTEM SHALL hand every racer online on that tick to the network hub, through the handoff of
`cloudnet/hub-handoff`. THE SYSTEM SHALL NOT move a racer to the lobby world for a finished cup. THE SYSTEM SHALL NOT hand off a
racer while the cup is still running.

#### Scenario: Two racers are handed to the hub after the results
- **WHEN** a two-map cup finishes while two racers are online, and 200 ticks pass after the cup result is announced
- **THEN** both racers are handed to the hub, and neither is moved to the lobby world

#### Scenario: Nothing is handed off while the results are shown
- **WHEN** the cup result is announced and 199 ticks have passed
- **THEN** no racer has been handed off

#### Scenario: A racer who joins during the linger is handed off too
- **WHEN** a third racer joins 100 ticks after the cup result, and the linger ends
- **THEN** all three racers are handed to the hub

#### Scenario: An empty server hands off nobody
- **WHEN** a cup finishes with no racer online
- **THEN** no handoff is made

### Requirement: A racer handed off does not start the next countdown
**MoSCoW:** Must. THE SYSTEM SHALL NOT begin a countdown for the racers it has handed to the hub. A racer who joins the server after
the handoff starts the room as the minimum rule says, as before.

#### Scenario: The room is empty after the handoff
- **WHEN** a cup finishes with two racers online and both are handed to the hub
- **THEN** the room is waiting with no racers and no countdown begins

#### Scenario: A later join starts the room as before
- **WHEN** after the handoff two racers join and the minimum is two
- **THEN** the room's countdown begins as it does for any two racers
