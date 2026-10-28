# Spec Delta

## Purpose

A server plays the cups of a configured rotation in order, one cup per round, inside one JVM. When a cup finishes and its results
have lingered, the next cup of the rotation is armed for the next round, and the first cup follows the last. Only one cup's worlds
are in heap at a time. A state file, when configured, keeps the position across a deploy or redeploy. Nothing else about a round
changes.

## ADDED Requirements

### Requirement: The rotation names the cups in play order
**MoSCoW:** Must. WHERE `VOYAGER_CUP_ROTATION` is set, THE SYSTEM SHALL read it as an ordered, comma-separated list of cup
identifiers, drop blank entries and surrounding spaces, and play the cups in the order given.

#### Scenario: Two cups in the listed order
- **WHEN** `VOYAGER_CUP_ROTATION` is `alpha_cup,legacy_cup`
- **THEN** the first round plays `alpha_cup` and the second round plays `legacy_cup`

#### Scenario: Blank entries are ignored
- **WHEN** `VOYAGER_CUP_ROTATION` is ` alpha_cup, ,legacy_cup `
- **THEN** the rotation holds `alpha_cup` and `legacy_cup`, in that order

### Requirement: A finished cup arms the next cup once its results have lingered
**MoSCoW:** Must. WHEN a cup of the rotation has finished, its results have lingered, and its racers have been handed to the network
hub, THE SYSTEM SHALL arm the next cup of the rotation for the next round, and WHEN the finished cup is the last of the rotation, THE
SYSTEM SHALL arm the first cup. THE SYSTEM SHALL keep the server running and SHALL NOT stop the process between cups.

#### Scenario: The rotation advances after a finished cup
- **WHEN** `alpha_cup` finishes, its results linger, and the rotation is `alpha_cup,legacy_cup`
- **THEN** `legacy_cup` is armed, and the next round plays `legacy_cup`

#### Scenario: The rotation wraps around
- **WHEN** `legacy_cup` finishes and the rotation is `alpha_cup,legacy_cup`
- **THEN** `alpha_cup` is armed, and the next round plays `alpha_cup`

#### Scenario: The next round starts in the same process
- **WHEN** a cup finishes and the next racers join the empty server
- **THEN** the round that starts plays the armed cup in the same JVM, and no restart of the process happens

### Requirement: An aborted or restarted cup does not advance the rotation
**MoSCoW:** Must. WHEN a cup of the rotation is aborted or restarted, THE SYSTEM SHALL keep the same cup as the armed cup.

#### Scenario: The last racer leaves
- **WHEN** `alpha_cup` is running and the last racer leaves, so the cup is aborted
- **THEN** the next round plays `alpha_cup` again

#### Scenario: An operator restarts the cup
- **WHEN** an operator restarts `legacy_cup` with `/race restart`
- **THEN** the restarted round plays `legacy_cup`, and the round after the cup that finishes plays the cup that follows `legacy_cup`

### Requirement: The rotation of one cup keeps the existing selection rules
**MoSCoW:** Must. WHILE `VOYAGER_CUP_ROTATION` is not set, THE SYSTEM SHALL select the cup as `VOYAGER_CUP` and the cup directory
select it today: a named cup plays alone, and an unnamed selection with more than one cup file is refused as ambiguous.

#### Scenario: Only VOYAGER_CUP is set
- **WHEN** `VOYAGER_CUP=alpha_cup` is set and `VOYAGER_CUP_ROTATION` is not
- **THEN** every round plays `alpha_cup`, and the rotation does not advance

#### Scenario: Neither key is set and two cup files exist
- **WHEN** neither key is set and the cup directory holds two cup files
- **THEN** boot is refused as ambiguous, as it is today

### Requirement: Both selection keys together refuse boot
**MoSCoW:** Must. IF `VOYAGER_CUP` and `VOYAGER_CUP_ROTATION` are both set, THEN THE SYSTEM SHALL refuse boot, and the message SHALL
name both keys and say that one of them must be removed.

#### Scenario: Both keys set
- **WHEN** `VOYAGER_CUP=alpha_cup` and `VOYAGER_CUP_ROTATION=alpha_cup,legacy_cup` are both set
- **THEN** boot is refused and the message names `VOYAGER_CUP` and `VOYAGER_CUP_ROTATION`

### Requirement: A rotation refuses an empty list and a cup named twice
**MoSCoW:** Must. IF `VOYAGER_CUP_ROTATION` names no cup after blank entries are dropped, OR names the same cup twice, OR names a cup
that resolves to no cup file, THEN THE SYSTEM SHALL refuse boot and name the offending entry.

#### Scenario: A cup named twice
- **WHEN** `VOYAGER_CUP_ROTATION=alpha_cup,alpha_cup`
- **THEN** boot is refused and the message names `alpha_cup` as listed twice

#### Scenario: An unknown cup
- **WHEN** `VOYAGER_CUP_ROTATION=alpha_cup,alhpa`
- **THEN** boot is refused and the message names `alhpa` and lists the available cups

### Requirement: Without a state file the rotation position is kept in the JVM only
**MoSCoW:** Must. WHILE `VOYAGER_CUP_ROTATION_STATE` is not set and the rotation holds more than one cup, THE SYSTEM SHALL keep the
position in memory, and SHALL log one WARN at boot that says a restart or redeploy begins again at the first cup.

#### Scenario: A deploy without a state file
- **WHEN** the rotation is `alpha_cup,legacy_cup` without a state file and `legacy_cup` was armed before the deploy
- **THEN** the boot plays `alpha_cup`, and the boot log holds the WARN about the missing state file

### Requirement: The state file keeps the armed cup across a deploy
**MoSCoW:** Should. WHERE `VOYAGER_CUP_ROTATION_STATE` names a file, THE SYSTEM SHALL write the canonical name of the armed cup to that
file each time a cup is armed, through a temporary file that is then moved over it, and SHALL read it at boot.

#### Scenario: The boot after a deploy plays the armed cup
- **WHEN** `alpha_cup` finishes and `legacy_cup` is armed with the state file set, the service is redeployed, and the rotation is unchanged
- **THEN** the boot plays `legacy_cup`

#### Scenario: A state file that cannot be written
- **WHEN** arming a cup cannot write the state file
- **THEN** the server logs one WARN that names the file, and the rotation continues in the JVM

### Requirement: A state file that names no cup of the rotation starts at the first cup
**MoSCoW:** Should. IF the state file cannot be read, OR names a cup that is not in the rotation, THEN THE SYSTEM SHALL start at the first
cup of the rotation and SHALL log one WARN that names the file.

#### Scenario: The state names a cup that was removed from the rotation
- **WHEN** the state file names `retired_cup` and the rotation is `alpha_cup,legacy_cup`
- **THEN** the boot plays `alpha_cup` and logs one WARN that names the state file

### Requirement: Every cup of the rotation is named the way VOYAGER_CUP names one
**MoSCoW:** Must. THE SYSTEM SHALL resolve each identifier of the rotation and the state file's name by the same rule that resolves
`VOYAGER_CUP`, so that a cup is named in the same way in every setting.

#### Scenario: A key-form identifier after the catalogue keys change
- **WHEN** the rotation names `elytrarace:legacy_cup` and the catalogue holds the cup `legacy_cup` under that key
- **THEN** the rotation resolves it to `legacy_cup`, and the state file stores `legacy_cup`

### Requirement: Only one cup's worlds are in heap at a time
**MoSCoW:** Must. WHEN the armed cup changes, THE SYSTEM SHALL release the worlds of the previous cup before it opens the worlds of the
armed cup, and SHALL keep a world that both cups share. THE SYSTEM SHALL hold the worlds of one cup and the lobby world at any time.

#### Scenario: Arming legacy_cup after alpha_cup
- **WHEN** `alpha_cup` finishes and its racers are handed to the hub, and `legacy_cup` is armed
- **THEN** the world `ElytraraceBlueAndRed` is released, and `PalmBeach`, `DragonLobby` and `Memoria` are opened, and no more than one cup's worlds are open at once

#### Scenario: A shared world is kept
- **WHEN** the previous cup and the armed cup both name the world `Shared`
- **THEN** `Shared` stays open and is not released or opened again

#### Scenario: A world of the armed cup fails to open
- **WHEN** a world of the armed cup cannot be opened when the cup is armed
- **THEN** the server logs one ERROR that names the world, and the rotation moves on to the next cup that opens

### Requirement: A round is never changed while it runs
**MoSCoW:** Must. WHILE a round is running, THE SYSTEM SHALL keep the cup and the catalogue it began with, and SHALL NOT arm a different
cup until that round finishes.

#### Scenario: A reload during a round
- **WHEN** a round of `alpha_cup` is running and the catalogue is reloaded
- **THEN** the round keeps playing `alpha_cup` from the catalogue it pinned, and the reloaded catalogue applies from the next round
