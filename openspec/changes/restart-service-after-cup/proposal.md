# Proposal

**Type and scope:** `feat(race)`. The change ends a cup's service on purpose, so it belongs to the race slice's cup end and
its hand-off to the hub (`platform.lobby`, `platform.handoff`), with the configuration key read by the composition root as
every setting is. Ships as one code change under the title `feat(race): restart the game service after each cup`. Breaking:
no. Off by default: a server that does not set the key behaves exactly as before.

## Why

The live CloudNet task `ElytraRace` is a dynamic service on one node: `autoDeleteOnStop: true`, `minServiceCount: 1`, heap
1024 MB. After a cup the server keeps running with its accumulated state: the armed cup, the loaded worlds, the statistics
queue and the JVM's heap. Nothing resets it between cups, and the owner wants a fresh service after each cup instead.

The cup rotation (#377, ADR-0046) already keeps the armed cup in a state file outside the service directory, so a fresh service
can play the next cup. What is missing is the restart itself: a clean end of the process once the racers have been handed to
the hub, which CloudNet answers by starting a new service from the template.

## What Changes

- A new setting `VOYAGER_RESTART_AFTER_CUP`, read through the configuration loader (ADR-0036). `true` turns the restart on; unset,
  blank or `false` keeps today's behaviour. Any other value refuses the start and the configuration check names the key.
- With the setting on and a rotation of more than one cup, `VOYAGER_CUP_ROTATION_STATE` is required. Without it every restart
  would play the first cup again, so the start is refused and the configuration check says why.
- When a cup finishes and its results have lingered (200 ticks, as today), the existing hand-off moves every racer online to the
  hub, the next cup of the rotation is armed and written to the state file, and then the server stops cleanly with exit status 0.
  CloudNet starts a fresh service.
- The stop happens only after the state file reads back the armed cup. If it does not, the server keeps running as it does today
  and logs one ERROR.
- After the hand-off the server waits until no player is connected, for at most 100 ticks (5 s), and then stops. A racer still
  connected at the limit is disconnected by the stop, and one WARN says how many.
- A player who joins while the restart is pending is handed to the hub at once. No cup starts while it is pending.
- The restart logs exactly one INFO line that names the finished cup, the next cup and the reason.

## Capabilities

### New Capabilities

- `restart-after-cup`: the setting, the restart at the end of a cup, the order of state write and stop, the drain, and the
  behaviour of joins while the restart is pending.

### Modified Capabilities

- `configuration` (`server/configuration`): the new key is read through the loader, listed in `ServerSettings.KEYS` and checked
  by the configuration check.

## Decisions taken in this proposal

- **The finale is not timed separately.** The finale plays for 80 ticks (`CupFinale.LENGTH_TICKS`), which is inside the 200-tick
  linger that already gates the hand-off. The restart rides on the hand-off, so it happens after the finale without a new timer.
- **The drain is 100 ticks, a constant and not a setting.** The hand-off is asynchronous on the CloudNet side, so an immediate stop
  could show the racers the stop's kick instead of the hub. One constant keeps the operator surface small.
- **An aborted cup does not restart the service.** The owner's wording is "after a cup finishes". An abort (the last racer leaves
  a running cup) keeps the service up as before. See the open question below.

## Open question for the owner

- Should an aborted cup also restart the service? Today an abort, for example when the last racer leaves a running cup, leaves
  the service up with the same cup armed. With the restart on, that service keeps the partial state of the aborted round. The
  proposal keeps the current behaviour; a change of answer adds one trigger and one scenario.

## Dependencies

- `rotate-cups-per-round` and ADR-0046 (merged as #377). The state file and the rotation store are used unchanged.
- ADR-0033 (`lobby-cup-display-and-hub-handoff`). The hand-off port and `HubHandoff` are used unchanged. The restart does not
  change the rule that a racer is not kicked on the normal path.
- `fix/wait-for-loading-racers-at-go` (another agent's branch, in progress). It touches the GO and lobby start path. This change
  keeps its edits out of that path: `WaitingRoom.tick`, the start verdict, `CupSession.start` and the GO logic are not changed.

## Out of scope

- The GO and lobby start path (see above), the race scoring, the rings, the maps and the cup's timings.
- The bridge and the CloudNet task on the node. Enabling the setting on the live task is an operator action after this change is
  merged, not part of the pull request.
- Restarting after an aborted cup (see the open question).
- Persisting the rotation or the restart reason in PostgreSQL.
- Kubernetes or Agones lifecycle hooks. A restart here is a clean process exit; the orchestrator decides what starts next.
- Changing the ADR-0046 position of the state file, or accepting ADR-0047 (this change adds it as Proposed).
- Archiving this change and ticking its tasks, which is a follow-up pull request the owner asks for.
