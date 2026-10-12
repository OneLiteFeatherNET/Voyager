# Delta for race/waiting-lobby

Changes the waiting-lobby contract: waiting racers stand in a dedicated lobby world instead of the first map's world,
they see a boss bar and a join line, and a full lobby shortens the start countdown. Requirements not listed here keep
their current text, including the start gate, the minimum, the countdown length, the commit window, the mid-cup join,
the between-maps rule, the abort, the DNF rule and the late-joiner rule.

## REMOVED Requirements

### Requirement: Waiting racers stand on the first map's spawn without flight equipment
**Reason**: Waiting racers stand in a lobby world now (ADR-0029), so the first map's spawn is no longer where they wait.
The "no lobby world" scenario is withdrawn with it.
**Migration**: Replaced by "Waiting racers stand in the lobby world without flight equipment" below. Without a configured
lobby world the server keeps the old placement and warns at boot, as specified in `server/lobby-world`.

## MODIFIED Requirements

### Requirement: Waiting racers are told how many are online and how many are needed
**MoSCoW:** Must. WHILE the minimum is not met, THE SYSTEM SHALL show every waiting racer, on the boss bar described in
"The room shows the count and the countdown on a boss bar", the number of racers online and the number needed to start.
THE SYSTEM SHALL NOT use the action bar for this line.

#### Scenario: Count shown while waiting
- **WHEN** one racer of a minimum of two is online and waiting
- **THEN** the racer's boss bar states that one racer is online and that two are needed, and the action bar stays empty

### Requirement: A finished cup returns the room to waiting
**MoSCoW:** Must. WHEN a cup finishes, THE SYSTEM SHALL return the room to waiting. THE SYSTEM SHALL hold each racer in
the lobby world at the lobby spawn, without flight equipment. IF enough racers are still online, THE SYSTEM SHALL begin
the next cup's countdown without an operator's action.

#### Scenario: The next cup starts on its own
- **WHEN** a two-map cup finishes while two racers are online and the minimum is two
- **THEN** the next cup's countdown begins and the racers are placed at the lobby spawn of the lobby world

#### Scenario: The next cup waits for enough racers
- **WHEN** a cup finishes while one racer is online and the minimum is two
- **THEN** the room waits for a second racer, the racer stands at the lobby spawn, and no countdown begins

## ADDED Requirements

### Requirement: Waiting racers stand in the lobby world without flight equipment
**MoSCoW:** Must. WHILE no cup is running, THE SYSTEM SHALL hold each online racer in the lobby world named by the
server's lobby setting, at the lobby spawn, without elytra and without rockets. THE SYSTEM SHALL NOT place a waiting racer
in the world of a catalogue map.

#### Scenario: A racer waits in the lobby world
- **WHEN** a racer joins while no cup is running and a lobby world is configured
- **THEN** the racer stands at the lobby spawn of the lobby world, with no elytra and no rockets in the inventory

#### Scenario: A waiting racer is not in a map's world
- **WHEN** the room is waiting with two racers online
- **THEN** no racer is in the world of any catalogue map

### Requirement: Joining racers are announced in chat with the online count
**MoSCoW:** Must. WHEN a racer joins, THE SYSTEM SHALL send every online racer the chat line
`<name> joined the game (x/y)`, where x is the number of racers online after the join and y is the maximum racer count.
This applies whether a cup is running or not.

#### Scenario: The first racer joins
- **WHEN** the first racer joins an empty server whose maximum is sixteen
- **THEN** the chat line reads that the racer joined the game (1/16)

#### Scenario: A racer joins a running cup
- **WHEN** a racer joins while a map is racing and three racers are online with a maximum of sixteen
- **THEN** every online racer receives the join line (4/16), and the joining racer is also told that the cup is running

### Requirement: The room shows the count and the countdown on a boss bar
**MoSCoW:** Must. WHILE a racer is in the lobby world, THE SYSTEM SHALL show that racer a boss bar stating the waiting
count and the maximum, or the seconds left of the start countdown. Where no lobby world is configured, THE SYSTEM SHALL
show the same bar to a waiting racer in the first map until the start is committed. THE SYSTEM SHALL update the boss
bar when its text or its progress changes, and SHALL hide it when the racer leaves the lobby world, or, without a lobby,
when the start is committed.

#### Scenario: The boss bar shows the waiting count
- **WHEN** one racer of a minimum of two is online and waiting
- **THEN** the racer's boss bar reads that one of two racers is online, and the bar stays until the racer leaves the lobby world

#### Scenario: The boss bar shows the countdown
- **WHEN** the minimum is met and ten seconds of the lobby remain
- **THEN** the racer's boss bar reads ten seconds to the start, and its progress is the fraction of the lobby that remains

#### Scenario: The boss bar leaves with the racer
- **WHEN** the first map is entered and a racer moves out of the lobby world
- **THEN** the lobby boss bar is hidden for that racer, and the race boss bar is the only one shown

#### Scenario: The fallback boss bar shows the count and the countdown in the first map
- **WHEN** no lobby world is configured and a racer waits at the first map's spawn with one racer of a minimum of two online
- **THEN** the racer's boss bar reads that one of two racers is online, and when the minimum is met the same bar reads the seconds to the start
- **AND** the bar is hidden for that racer once the start is committed, so the race boss bar is the only one shown from then on

### Requirement: A full lobby shortens the start countdown
**MoSCoW:** Must. IF the online racer count reaches the maximum racer count while the first map's lobby is counting down
AND more than the full-lobby countdown length of that lobby remains, THEN THE SYSTEM SHALL shorten the countdown to the
full-lobby countdown length of 10 seconds. THE SYSTEM SHALL NOT lengthen a countdown, and SHALL NOT shorten a countdown
after the start is committed.

#### Scenario: A full lobby shortens a long countdown
- **WHEN** the sixteenth racer joins a server with a maximum of sixteen while fifteen seconds of the lobby remain
- **THEN** the countdown is shortened to ten seconds from that tick, and the boss bar shows ten seconds

#### Scenario: A short countdown is left alone
- **WHEN** the sixteenth racer joins while six seconds of the lobby remain
- **THEN** the countdown still ends after six seconds

#### Scenario: A commit is not shortened
- **WHEN** the lobby is full with two seconds of the lobby remaining
- **THEN** the countdown is unchanged and the start remains committed

#### Scenario: A racer leaving does not restore the longer countdown
- **WHEN** the lobby was shortened to ten seconds and one racer leaves with nine seconds left
- **THEN** the countdown is cancelled as in the cancel requirement, and when the lobby is full again the full lobby is shortened to ten seconds

### Requirement: The maximum racer count does not reject a joining racer
**MoSCoW:** Must. THE SYSTEM SHALL NOT reject a racer because the server holds the maximum racer count, whether a cup
is running or not. The maximum racer count sets the join line and the full-lobby countdown only. A hard cap that refuses joins is not built
in this change.

#### Scenario: A join beyond the maximum is accepted
- **WHEN** a seventeenth racer joins a server whose maximum is sixteen
- **THEN** the connection is accepted, the join line reads (17/16), and the racer waits in the lobby world
