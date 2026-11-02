# ADR-0047: The game service ends its process after each finished cup when `VOYAGER_RESTART_AFTER_CUP` is set, so that the node starts a fresh one

## Status

Proposed

The owner accepts or rejects this record. The change that implements it is `restart-service-after-cup` (OpenSpec). Status moves to
Accepted only on the owner's word. It refines ADR-0046: with this setting on, a server does not stay up between cups.

## Date

2026-11-02

## Decision makers

- TheMeinerLP (project owner). On 2026-11-02 the owner decided that after a cup the service is handed to a fresh start: every racer
  goes to the hub, the service stops cleanly, and the node starts a new one that plays the next cup of the rotation.

## Context and problem statement

ADR-0046 keeps the cup rotation inside one server process, so the server stays up between cups, and the position of the rotation
survives a restart through an optional state file. The live CloudNet task `ElytraRace` runs one dynamic service on one node. After
a cup the service keeps everything it has built up: the armed cup, the loaded worlds, the statistics queue and the heap. Nothing
resets that between cups, and the owner wants each cup to begin in a fresh service.

The question is how the server ends its process at the right moment, without losing the racers or the next cup, and how it is
turned on for the live task only.

## Decision drivers

- The racers of a finished cup are never kicked on the normal path (ADR-0033): they are sent to the hub before the stop.
- The next cup must be written before the process stops, so the fresh service plays it (ADR-0046).
- The change must be off by default, so local development and the tests keep their behaviour.
- The stop must be the clean shutdown the node already uses (`stop` on standard input), so CloudNet sees a normal exit.
- No new dependency on CloudNet or the proxy in the server (ADR-0026, ADR-0033).

## Considered options

- **(a) Keep running between cups (the state of ADR-0046).** Rejected for the live task: the owner wants the reset. Kept as the default.
- **(b) End the process after each finished cup, through the existing clean shutdown, with the state written first (chosen).** The
  hand-off and the arming already exist; the restart adds the order, the drain and the stop. CloudNet starts the next service from the
  template.
- **(c) Reset the server in process: close the worlds, clear the statistics queue and reload the catalogue.** Rejected: the state that
  accumulates is spread over the heap, the Minestom instances, the statistics threads and the boss bars. A reset that misses one of
  them is the same problem as today, and it is hard to prove.
- **(d) Ask CloudNet's REST API from the server to restart the service.** Rejected: it needs a credential in the service, and the stop
  line is already the node's own contract for ending a service.
- **(e) Restart on a timer or when the server is empty.** Rejected: the owner asked for the restart after each cup, and an empty server
  is not a finished cup.

## Decision outcome

Chosen: option (b), off by default, turned on with `VOYAGER_RESTART_AFTER_CUP=true`.

When a cup has finished and its results have lingered (200 ticks, ADR-0033):

1. every racer online is handed to the hub, as before;
2. the next cup of the rotation is armed and written to the state file (ADR-0046);
3. the state is read back, and only if it holds the next cup does the server ask for the restart; a state that does not read back keeps
   the server running and logs one ERROR;
4. the server waits, for at most 100 ticks (5 s), until no player is connected;
5. the server stops through the clean shutdown, with exit status 0.

Two rules follow. A player who joins while the restart is pending is handed to the hub at once, so no cup starts. With more than one cup
in the rotation the setting requires `VOYAGER_CUP_ROTATION_STATE`, because a restart without the state would begin again at the first
cup and the rotation would never reach its second.

The finale runs for 80 ticks inside the 200-tick linger, so the restart comes after the finale without a timer of its own. An aborted cup
does not restart the service, because the restart is for a finished cup; this is an open question for the owner in the change.

## Consequences

- Good: each cup begins in a process that has none of the previous cup's state, and CloudNet does the restart with its own start path.
- Good: the change touches no bridge code and no hand-off port; the stop is the one the node already sends.
- Good: default off, so local development and the existing golden transcripts are unchanged.
- Bad: the first racers of the next cup wait while the service starts and loads its worlds. The length of that wait is not measured here.
- Bad: the drain can hold a stop for up to 5 s while a racer is still connected; then the stop disconnects the racer, with one WARN.
- Bad: an aborted cup keeps the service up, so the partial state of an aborted round is not reset until the next finished cup.
- Neutral: the statistics queue drains before the process exits, under the existing shutdown (`StatsBinding`, 10 s at most).

## Confirmation

`CupRestartTest` pins the state machine (the read-back, the single accepted request, the drain and its one WARN). `WaitingRoomTest`
pins the order of hand-off, arming and request, the empty server, the aborted cup and the join during the restart. `RestartSettingsTest`
pins the parse and the configuration rules. `RestartWiringTest` pins the graph bean. The smoke boot of the change records the boot line
and the clean exit. The configuration check runs record the refusals.
