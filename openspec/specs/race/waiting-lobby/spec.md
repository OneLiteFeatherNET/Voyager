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

### Requirement: Waiting racers stand on the first map's spawn without flight equipment
**MoSCoW:** Must. WHILE no cup is running, THE SYSTEM SHALL hold each online racer in the first map's world of the
current catalogue at that map's spawn, without elytra and without rockets. THE SYSTEM SHALL NOT create a lobby world.

#### Scenario: A racer waits on the spawn
- **WHEN** a racer joins while no cup is running
- **THEN** the racer stands at the first map's spawn with no elytra and no rockets in the inventory

#### Scenario: No world is created for the waiting room
- **WHEN** the server starts with a catalogue of two maps
- **THEN** the waiting room uses the first map's world and no world beyond the cup's maps is opened

### Requirement: Waiting racers are told how many are online and how many are needed
**MoSCoW:** Must. WHILE the minimum is not met, THE SYSTEM SHALL show every waiting racer the number of racers online
and the number needed to start.

#### Scenario: Count shown while waiting
- **WHEN** one racer of a minimum of two is online and waiting
- **THEN** the racer is shown that one racer is online and that two are needed

### Requirement: The start countdown is the first map's lobby
**MoSCoW:** Must. WHEN a cup starts from waiting, THE SYSTEM SHALL play the first map's lobby for the lobby length of
the run's race timings, with the existing three-digit countdown and the launch on the tick the lobby ends.

#### Scenario: Production countdown length
- **WHEN** the minimum is met at the start of a production run
- **THEN** the first map launches 20 seconds later, with the countdown digits shown in its last three seconds

#### Scenario: Dev countdown length
- **WHEN** the minimum is met under dev mode
- **THEN** the first map launches 10 seconds later

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
**MoSCoW:** Must. WHEN a cup finishes, THE SYSTEM SHALL return the room to waiting. IF enough racers are still online,
THE SYSTEM SHALL begin the next cup's countdown without an operator's action.

#### Scenario: The next cup starts on its own
- **WHEN** a two-map cup finishes while two racers are online and the minimum is two
- **THEN** the next cup's countdown begins and the racers are placed at the first map's spawn

#### Scenario: The next cup waits for enough racers
- **WHEN** a cup finishes while one racer is online and the minimum is two
- **THEN** the room waits for a second racer and no countdown begins

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
