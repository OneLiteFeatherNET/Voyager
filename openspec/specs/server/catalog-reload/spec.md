# server/catalog-reload Specification

## Purpose
Defines how a running Voyager server applies changed map and cup definitions without a restart. The server
applies a reload only at a round boundary, applies it all or nothing, and keeps the last good catalogue when a
reload fails. Players see the effect only as the next round using the new maps. Operators see it as one report
per reload. Requirements use EARS patterns and MoSCoW priorities, as `openspec/config.yaml` requires.

Terms: a **round** is one cup run, from the start of the cup until it finishes or is stopped. A **snapshot** is
the immutable set of the selected cup and the maps it plays, as read from disk at one time.

## Requirements

### Requirement: An operator triggers a reload from the console or the game
**Priority:** MoSCoW Must

WHEN an operator with the permission `voyager.race.reload` (or the console) issues `/race reload`, THE SYSTEM
SHALL read and validate the catalogues off the tick thread and SHALL report the outcome to that sender.

#### Scenario: Valid edit is accepted and reported
- **WHEN** an operator runs `/race reload` after changing the ring count of a map to a valid value
- **THEN** the sender receives one message stating that the reload is pending for the next round
- **AND** the running round keeps its previous map

#### Scenario: Sender without permission is refused
- **WHEN** a player without `voyager.race.reload` runs `/race reload`
- **THEN** the player receives a refusal message
- **AND** no catalogue file is read and no snapshot is replaced

### Requirement: A running round keeps the snapshot it started with
**Priority:** MoSCoW Must

WHILE a round is running, THE SYSTEM SHALL use only the snapshot that the round started with, for its cup,
its rotation, its maps and its rings, and SHALL NOT change any of them until the round ends.

#### Scenario: Reload during a round does not change the round
- **WHEN** a reload is applied while map 2 of 3 is running
- **THEN** maps 2 and 3 of the running round use the previous definitions
- **AND** the status shown by `/race` reports the reload as pending

#### Scenario: A reload never changes the rotation of a round
- **WHEN** a valid reload changes the list of maps in the cup
- **THEN** the running round plays its original rotation to the end

### Requirement: A pending snapshot takes effect at the start of the next round
**Priority:** MoSCoW Must

WHEN a round starts and a valid pending snapshot exists, THE SYSTEM SHALL make the pending snapshot the current
snapshot and SHALL start the round with it, in one step that no reader can observe half-done.

#### Scenario: Next round uses the changed map
- **WHEN** a valid reload was applied during round 1 and round 2 starts
- **THEN** round 2 plays the changed map definition

#### Scenario: No round starts and the pending snapshot waits
- **WHEN** a valid reload is applied and no further round starts
- **THEN** the pending snapshot is kept and the running state is unchanged

### Requirement: A broken catalogue keeps the last good snapshot
**Priority:** MoSCoW Must

IF a reload finds a malformed file or duplicate name in `maps/` or the cup directory, an empty map directory, a
malformed or unresolvable played cup, a played cup entry naming an unknown map, a selection that names no cup or an
ambiguous one, or a world of the played cup that holds no region data or cannot be opened, THEN THE SYSTEM SHALL keep
the current snapshot, SHALL discard the candidate, and SHALL report every such problem in one message to the sender.

#### Scenario: Malformed map file is reported and nothing changes
- **WHEN** a map file is saved with invalid JSON and `/race reload` runs
- **THEN** the sender receives the file name in the report
- **AND** the current snapshot and the running cup are unchanged

#### Scenario: Several problems are listed together
- **WHEN** two map files are malformed and the played cup names an unknown map
- **THEN** the report lists all three problems in one message

#### Scenario: Malformed played cup is rejected
- **WHEN** the played cup file is saved with invalid JSON and `/race reload` runs
- **THEN** the sender receives the cup file name in the report
- **AND** the current snapshot and the running cup are unchanged

### Requirement: A broken unplayed cup warns and does not refuse a reload
**Priority:** MoSCoW Must

IF the only problem found by a reload is in a cup that is not played, THEN THE SYSTEM SHALL NOT refuse the reload.
It SHALL apply the reload and SHALL add one warning line to the sender's reply that names each such cup and each of
its problems, as boot logs them.

#### Scenario: Malformed unplayed cup warns and the reload is applied
- **WHEN** a cup that is not played is saved with invalid JSON, a map edit is valid, and `/race reload` runs
- **THEN** the reload is applied with the new map definitions
- **AND** the sender's reply contains one warning line that names the unplayed cup file and its problem
- **AND** the running round keeps its previous snapshot until the next round starts

#### Scenario: Unplayed cup naming an unknown map warns and the reload is applied
- **WHEN** a cup that is not played names a map that no map file provides, and `/race reload` runs
- **THEN** the reload is applied
- **AND** the sender's reply contains one warning line that names that cup and the unknown map

### Requirement: A reload failure never stops the server
**Priority:** MoSCoW Must

IF reading, validating or opening a world throws an exception during a reload, THEN THE SYSTEM SHALL log the
failure once at ERROR with its cause, SHALL keep the current snapshot, and SHALL keep the tick loop running.

#### Scenario: Unexpected exception during reload
- **WHEN** the reader throws an unchecked exception during a reload
- **THEN** the server keeps ticking
- **AND** the sender receives a message that the reload failed and the cause is in the log

### Requirement: A reload never blocks a tick
**Priority:** MoSCoW Must

THE SYSTEM SHALL perform catalogue reads, parsing, validation and world checks outside the tick task, so that no
reload work runs within the 50 ms budget of one server tick.

#### Scenario: Reload runs off the tick thread
- **WHEN** a reload is requested
- **THEN** the reading and parsing run on a thread other than the server tick task

### Requirement: Reloadable and non-reloadable world changes
**Priority:** MoSCoW Must

WHEN a reload references a world that is not open yet, THE SYSTEM SHALL open and validate that world before it
applies the snapshot. WHEN a reload references a world that is already open, THE SYSTEM SHALL apply the map
definition change and SHALL NOT reread or reopen that world's region data.

#### Scenario: New map with a new world is accepted
- **WHEN** a new map file names a world that holds region data and is not open yet
- **THEN** the world is opened and validated, and the reload is applied

#### Scenario: New map with a world without region data is rejected
- **WHEN** a new map file names a world with no region data
- **THEN** the reload is rejected and the report names the world

#### Scenario: Changed region data of an open world needs a restart
- **WHEN** region files of a world that is already open change on disk
- **THEN** the report states that a restart is needed for the world change
- **AND** the map definitions of the reload are still applied

### Requirement: Reload state is visible to operators
**Priority:** MoSCoW Should

WHILE a pending snapshot exists, THE SYSTEM SHALL show in the `/race` status that the reload waits for the next
round, and SHALL show the load time of the current snapshot.

#### Scenario: Status shows the pending reload
- **WHEN** a valid reload is pending and an operator runs `/race`
- **THEN** the status states that a reload waits for the next round
