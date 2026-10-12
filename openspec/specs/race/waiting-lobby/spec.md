# race/waiting-lobby Specification

## Purpose
Defines when a cup of the Voyager race may start, where waiting racers stand and what they are told, how the start
countdown is cancelled or committed, and what happens when racers leave, join mid-cup, or a cup finishes. It is the
player-facing contract of the lobby that comes before every cup.

## Requirements

### Requirement: A cup starts only when enough racers are online
**MoSCoW:** Must. THE SYSTEM SHALL NOT start a cup because a racer joined while fewer racers than the minimum are online.
WHEN the number of online racers reaches the minimum AND no cup is running, THE SYSTEM SHALL begin the start countdown.

#### Scenario: One racer joins an empty server with a minimum of two
- **WHEN** a single racer joins and no cup is running
- **THEN** no cup starts and the racer is told how many racers are still needed

#### Scenario: The second racer completes the minimum
- **WHEN** a second racer joins while one racer is waiting and the minimum is two
- **THEN** the start countdown begins for the first map of the cup

### Requirement: The minimum racer count has a production and a dev default
**MoSCoW:** Must. THE SYSTEM SHALL read the minimum racer count from the setting `VOYAGER_MIN_PLAYERS`, which defaults
to 2 and to 1 when `voyager.dev` is set. An explicit value SHALL override the default in both modes.

#### Scenario: Production default
- **WHEN** the server starts without `VOYAGER_MIN_PLAYERS` and without `voyager.dev`
- **THEN** the minimum racer count is 2

#### Scenario: Dev default
- **WHEN** the server starts with `voyager.dev` and without `VOYAGER_MIN_PLAYERS`
- **THEN** the minimum racer count is 1

#### Scenario: Explicit value under dev mode
- **WHEN** the server starts with `voyager.dev` and `VOYAGER_MIN_PLAYERS=3`
- **THEN** the minimum racer count is 3

### Requirement: Waiting racers are told how many are online and how many are needed
**MoSCoW:** Must. WHILE the minimum is not met, THE SYSTEM SHALL show every waiting racer, on the boss bar described in
"The room shows the count and the countdown on a boss bar", the number of racers online and the number needed to start.
THE SYSTEM SHALL NOT use the action bar for this line.

#### Scenario: Count shown while waiting
- **WHEN** one racer of a minimum of two is online and waiting
- **THEN** the racer's boss bar states that one racer is online and that two are needed, and the action bar stays empty

### Requirement: The start countdown is the first map's lobby
**MoSCoW:** Must. WHEN a cup starts from waiting, THE SYSTEM SHALL play the first map's lobby for the lobby length of
the run's race timings, with the existing three-digit countdown. ON the GO tick, THE SYSTEM SHALL release each racer to
fly on their own: THE SYSTEM SHALL give no velocity and SHALL NOT set the racer gliding, and the racer keeps the elytra
and rockets given during the countdown.

#### Scenario: Production countdown length
- **WHEN** the minimum is met at the start of a production run
- **THEN** the first map's GO tick comes 20 seconds later, with the countdown digits shown in its last three seconds

#### Scenario: Dev countdown length
- **WHEN** the minimum is met under dev mode
- **THEN** the first map's GO tick comes 10 seconds later

#### Scenario: GO gives no impulse
- **WHEN** the GO tick of the first map is reached
- **THEN** each racer's velocity is zero and the racer is not gliding

#### Scenario: Racer keeps the equipment at GO
- **WHEN** the GO tick of a map is reached
- **THEN** each racer still holds the elytra in the chest slot and the rockets in the hotbar

#### Scenario: GO tells the racer how to take off
- **WHEN** the GO tick of a map is reached
- **THEN** each racer is shown the GO title and, in the same tick, a subtitle telling them to jump and deploy the elytra
  (translation key `voyager.race.go.jump`)

#### Scenario: Racer takes off by deploying the elytra
- **WHEN** a racer on the GO tick deploys the elytra
- **THEN** the racer is gliding from that tick, and the race clock is the one that started on GO

### Requirement: Dropping below the minimum cancels the countdown before its last three seconds
**MoSCoW:** Must. IF the online racer count falls below the minimum during the first map's lobby AND more than three
seconds of that lobby remain, THEN THE SYSTEM SHALL cancel the countdown, return the room to waiting, and restart the
full lobby length when the minimum is met again.

#### Scenario: A racer leaves with ten seconds left
- **WHEN** one of two racers leaves while ten seconds of the lobby remain
- **THEN** the countdown is cancelled, the remaining racer is told the count is one of two, and no map has been entered

#### Scenario: The full lobby restarts after a cancel
- **WHEN** a second racer joins again after the cancel
- **THEN** the countdown begins again from its full length

### Requirement: The countdown is committed in its last three seconds
**MoSCoW:** Must. IF the online racer count falls below the minimum after the three-digit countdown has begun, THEN
THE SYSTEM SHALL keep the countdown running and launch the first map with the racers still online.

#### Scenario: A racer leaves with two seconds left
- **WHEN** one of two racers leaves while two seconds of the lobby remain
- **THEN** the countdown continues and the first map launches for the remaining racer

### Requirement: Racers who join a running cup wait for the next map
**MoSCoW:** Must. WHILE a cup is running, THE SYSTEM SHALL NOT move a joining racer into a race and SHALL tell the racer
that the cup is running and that they join at the next map. The racer enters the next map when it starts.

#### Scenario: A racer joins during a map
- **WHEN** a racer joins while a map's race phase is running
- **THEN** the racer is told the cup is running, holds no run, and is moved into the next map when it starts

### Requirement: The minimum does not gate a cup between its maps
**MoSCoW:** Must. WHILE a cup is running, THE SYSTEM SHALL continue the cup while at least one racer is online, whatever
the minimum is.

#### Scenario: One of two racers leaves between maps
- **WHEN** one of two racers leaves during the results screen between two maps
- **THEN** the cup continues with the remaining racer on the next map

### Requirement: The cup is aborted when the last racer leaves
**MoSCoW:** Must. WHEN the last online racer leaves while a cup is running, THE SYSTEM SHALL stop the cup without
announcing a cup result, and return the room to waiting.

#### Scenario: Everyone leaves mid-cup
- **WHEN** the last racer disconnects during the second map of a three-map cup
- **THEN** the cup stops, no cup result is announced, and the room is waiting for the minimum again

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

### Requirement: A racer who has not finished when a map ends is recorded as DNF
**MoSCoW:** Must. WHEN a map's race phase ends and a racer has not crossed the finish, THE SYSTEM SHALL score that
racer's map as DNF. The map earns ring points only and does not count as a finished map for the cup.

#### Scenario: A racer still flying at the race cap
- **WHEN** a map's race phase reaches its time cap with one racer unfinished
- **THEN** that racer's map result is DNF with its ring points and no medal points, and the cup's finished-map count does not include it

### Requirement: Late joiners are never rejected or spectated
**MoSCoW:** Won't (this change). THE SYSTEM SHALL NOT reject a racer who joins a running cup and SHALL NOT offer a
spectator mode. A configurable rejection or spectator option is not built here.

#### Scenario: A late join is accepted
- **WHEN** a racer joins a running cup
- **THEN** the connection is accepted and the racer waits for the next map

### Requirement: The map time limit stays at the cup's race cap
**MoSCoW:** Won't (this change). THE SYSTEM SHALL end a map's race phase at the cup's race length and SHALL NOT compute a
per-map limit from the map's reference time in this change.

#### Scenario: A map ends at the cup's race cap
- **WHEN** a map's race phase is running and no racer has finished
- **THEN** the phase ends when the cup's race length is reached, whatever the map's reference time is

### Requirement: The operator start overrides the minimum
**MoSCoW:** Should. WHEN an operator starts the cup with `/race start`, THE SYSTEM SHALL start the cup from its first map
with the lobby skipped, whatever the number of online racers.

#### Scenario: Operator start with one racer
- **WHEN** an operator runs `/race start` with one racer online and a minimum of two
- **THEN** the cup starts from its first map with no lobby wait

### Requirement: The lobby countdown reads whole seconds of wall-clock time
**MoSCoW:** Should. THE SYSTEM SHALL display the remaining lobby time in seconds of wall-clock time, derived from the phase
length as a duration, so that a 20 second lobby is never shown as 20 minutes or as ticks.

#### Scenario: Six seconds left
- **WHEN** six seconds of the lobby remain
- **THEN** the waiting display reads 6 and not a count of ticks

### Requirement: Once a cup has started, its per-tick behaviour is unchanged
**MoSCoW:** Must. ONCE a cup has started, THE SYSTEM SHALL play each tick as the cup-session-characterization capability
specifies, and the lobby gate SHALL NOT change the tick order of that capability.

#### Scenario: The golden scenarios still pass
- **WHEN** the cup-session golden master scenarios run after this change
- **THEN** every transcript equals its committed golden file byte for byte

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
