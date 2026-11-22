# Spec Delta

## Purpose

With `VOYAGER_RESTART_AFTER_CUP` set to `true`, a server ends its process after each finished cup, so that the service CloudNet
starts next is fresh. The racers are handed to the hub first, the next cup of the rotation is written to the state file before the
stop, and the stop is the clean shutdown that exits with status 0. With the setting off, nothing changes.

## ADDED Requirements

### Requirement: The restart is opt-in and off by default
**MoSCoW:** Must. WHILE `VOYAGER_RESTART_AFTER_CUP` is unset, blank or `false`, THE SYSTEM SHALL keep running after a cup finishes,
as it does without this change.

#### Scenario: The setting is not set
- **WHEN** a cup finishes and `VOYAGER_RESTART_AFTER_CUP` is not set
- **THEN** the server keeps running, no stop is requested, and the golden transcripts of a single-cup server are unchanged

#### Scenario: The setting is false
- **WHEN** `VOYAGER_RESTART_AFTER_CUP` is `false`
- **THEN** the behaviour is the same as when the setting is not set

### Requirement: The setting accepts only true or false
**MoSCoW:** Must. IF `VOYAGER_RESTART_AFTER_CUP` is set to anything other than `true` or `false`, ignoring case, THEN THE SYSTEM
SHALL refuse the start, and the configuration check SHALL name the key.

#### Scenario: A typo is refused
- **WHEN** `VOYAGER_RESTART_AFTER_CUP` is `ture`
- **THEN** the start is refused, and the error names `VOYAGER_RESTART_AFTER_CUP`

### Requirement: A restart with a rotation needs the state file
**MoSCoW:** Must. IF `VOYAGER_RESTART_AFTER_CUP` is `true`, `VOYAGER_CUP_ROTATION` lists more than one cup, and
`VOYAGER_CUP_ROTATION_STATE` is not set, THEN THE SYSTEM SHALL refuse the start, because every restart would play the first cup.

#### Scenario: Two cups and no state file
- **WHEN** the setting is `true`, the rotation is `alpha_cup,legacy_cup`, and no state file is set
- **THEN** the start is refused, and the error names `VOYAGER_RESTART_AFTER_CUP` and `VOYAGER_CUP_ROTATION_STATE`

#### Scenario: One cup needs no state file
- **WHEN** the setting is `true` and `VOYAGER_CUP=alpha_cup` is set
- **THEN** the start is accepted without a state file

### Requirement: The racers are handed to the hub before the restart
**MoSCoW:** Must. WHEN a cup has finished and its results have lingered, THE SYSTEM SHALL hand every racer online to the network
hub, and SHALL request the restart only after that hand-off has been asked for.

#### Scenario: Every racer is handed away
- **WHEN** three racers are online when the results linger ends
- **THEN** all three are asked for the hub move before the restart is requested

### Requirement: The restart follows the lingering results
**MoSCoW:** Must. WHEN the results of a finished cup have lingered for their full length, THE SYSTEM SHALL arm the next cup and
request the restart. THE SYSTEM SHALL NOT shorten the finale or the linger to make the restart come sooner.

#### Scenario: The finale is inside the linger
- **WHEN** a cup finishes
- **THEN** the finale plays for its full length, the linger lasts its full 200 ticks, and the restart is requested at the end of the
  linger

### Requirement: The next cup is written before the stop
**MoSCoW:** Must. THE SYSTEM SHALL write the armed cup to the state file before it requests the stop, and SHALL request the stop
only when the state file reads back the armed cup.

#### Scenario: The state is written first
- **WHEN** a finished cup arms `legacy_cup` with the rotation `alpha_cup,legacy_cup`
- **THEN** the state file holds `legacy_cup` before the stop is requested

#### Scenario: The state does not read back
- **WHEN** the state file cannot be written, so it does not read back `legacy_cup`
- **THEN** the stop is not requested, the server keeps running with `legacy_cup` armed in memory, and one ERROR names the state file

### Requirement: The server waits for the racers to leave, then stops
**MoSCoW:** Must. AFTER the hand-off, THE SYSTEM SHALL wait until no player is connected, for at most 100 ticks, and then request the
clean shutdown. THE SYSTEM SHALL stop at the limit even when a player is still connected, and SHALL log one WARN that names how many.

#### Scenario: Everybody leaves at once
- **WHEN** the hub moves every racer within 20 ticks
- **THEN** the stop is requested as soon as the last racer has left

#### Scenario: A racer stays connected
- **WHEN** a racer is still connected after 100 ticks
- **THEN** the stop is requested on the 100th tick, and one WARN names the one player still connected

### Requirement: The stop is the clean shutdown with exit status 0
**MoSCoW:** Must. WHEN the restart is requested, THE SYSTEM SHALL stop through the existing clean shutdown, so that the process exits
with status 0 and the node starts a new service.

#### Scenario: The process exits normally
- **WHEN** the drain ends
- **THEN** the game server stops cleanly and the process exits with status 0

### Requirement: An empty server still restarts
**MoSCoW:** Must. WHEN a finished cup's linger ends with no player online, THE SYSTEM SHALL arm the next cup, write the state, and
request the restart.

#### Scenario: The last racer left during the finale
- **WHEN** the cup finishes and every racer has left before the linger ends
- **THEN** the next cup is armed, the state is written, and the stop is requested once the drain has counted zero players

### Requirement: A player who joins during the restart is handed to the hub
**MoSCoW:** Must. WHILE the restart is pending, WHEN a player joins, THE SYSTEM SHALL hand that player to the network hub at once, SHALL
NOT count the player towards a cup, and SHALL NOT start a cup.

#### Scenario: A join after the restart is requested
- **WHEN** a player joins after the restart has been requested and before the process stops
- **THEN** the player is asked for the hub move, no countdown starts, and the restart is not delayed by a new cup

#### Scenario: A join when the hub move is refused
- **WHEN** a player who joins during the restart is refused by the hub move
- **THEN** the player is disconnected with the translated hand-off message, and the restart continues

### Requirement: A restart logs one INFO line with its reason
**MoSCoW:** Must. WHEN the restart is requested, THE SYSTEM SHALL log exactly one INFO line that names the finished cup, the next cup
and the reason, which is that the cup finished and the service restarts for a fresh start. At boot, WHILE the setting is `true`, THE
SYSTEM SHALL log one INFO line that says the restart is on; that line is not a restart reason.

#### Scenario: The restart reason
- **WHEN** `alpha_cup` finishes and `legacy_cup` is armed
- **THEN** one INFO line names `alpha_cup`, `legacy_cup` and the cup-finished reason

### Requirement: An aborted cup does not restart the service
**MoSCoW:** Must. WHEN a running cup is aborted, THE SYSTEM SHALL keep running as it does without this change, and SHALL NOT request
the restart.

#### Scenario: The last racer leaves a running cup
- **WHEN** the last racer leaves a running cup and the cup is aborted
- **THEN** no restart is requested and the server keeps running with the same cup armed
