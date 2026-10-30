# ADR-0044: The cup rotation runs in the server process, with an optional state file outside the service directory

## Status

Proposed

The owner accepts or rejects this record. The change that implements it is `rotate-cups-per-round` (OpenSpec). Status moves to
Accepted only on the owner's word.

## Date

2026-10-29

## Decision makers

- TheMeinerLP (project owner). The owner settled the mechanism and the state file in the proposal of `rotate-cups-per-round`.

## Context and problem statement

One Voyager server plays one cup. The owner wants a server to play the cups of a list in turn, one cup per round, so that
`alpha_cup` and then `legacy_cup` are played alternately. The setting `VOYAGER_CUP` names a single cup, and changing it means
changing the task's configuration.

The live task `ElytraRace` is a dynamic CloudNet service on one node, with `autoDeleteOnStop` set. Nothing stops the service
after a cup: the server keeps running and its lobby waits for the next racers. The service restarts only when it is deployed,
and a restart starts from a fresh copy of the template, so the service directory cannot hold anything that must survive a
deploy.

The question is where the position of the rotation (the armed cup, the one the next round plays) lives, and what keeps it when
the process restarts.

## Decision drivers

- A round must not be interrupted: the server stays up between cups, as it does today.
- The position must survive a deploy, if an operator wants it to.
- The rotation must not depend on the statistics database, which is optional (`VOYAGER_PERSISTENCE`).
- Heap: one cup's worlds plus the lobby are in heap at a time, because a 1024 MB heap has to hold the largest cup.

## Considered options

- **(c) In the process, as the primary mechanism (chosen).** The server arms the next cup in the same JVM when the previous
  cup's results have lingered and its racers have been handed to the hub. No file, database or node change is needed.
- **(a) A counter in the service directory.** Rejected: the directory is cleared on every stop, so the counter is gone on the
  next start.
- **(a') A state file outside the service directory (chosen as the optional keep-across-deploy mechanism).** The path comes
  from `VOYAGER_CUP_ROTATION_STATE`. It is written atomically (a temporary file, moved over the state) and read at the boot.
- **(b) A deterministic choice without state.** Rejected: a seeded random choice can repeat a cup back to back, and a
  time-slot choice does not map a round to the next cup.
- **(d) The last finished cup from PostgreSQL.** Rejected for now: it would tie the rotation to `VOYAGER_PERSISTENCE` and to a
  database being up at boot.

## Decision outcome

The rotation is in the process. `VOYAGER_CUP_ROTATION` names the cups in play order. When a cup has finished, its results have
lingered and its racers have been handed to the hub, the next cup is armed: the previous cup's worlds are released, the armed
cup's worlds are opened (a world both cups need is kept), the catalogue of the armed cup is offered to the holder for the next
round, and the name is written to the state file when one is configured.

- An aborted or restarted cup does not advance the rotation.
- A cup whose world folders are missing is skipped at boot with one warning. Boot is refused only when no cup of the rotation can
  be played, or when a listed cup has a definition error or a damaged world.
- `VOYAGER_CUP` and `VOYAGER_CUP_ROTATION` together refuse boot.
- The state file is optional. Without it a restart or redeploy begins again at the first cup, and the server says so at the boot.
  An unreadable file or a name that is not a playable cup of the rotation starts at the first playable cup with a warning. A
  write that fails logs one warning and the rotation carries on in memory.

## Consequences

- Good: no change to the CloudNet task beyond two environment variables and one directory on the node, and nothing depends on the
  statistics database.
- Good: a server that sets `VOYAGER_CUP` plays the cup it always played, with the same boot and the same check.
- Good: at most one cup's worlds and the lobby are in heap, so a 1024 MB heap is the bound the design is checked against.
- Bad: a release waits while a racer still stands in a world to be released, so the next cup's worlds may be loaded on demand
  for the first racers of the next round. The rotation logs what it released and opened, so the operator can see it.
- Bad: a restart without the state file begins again at the first cup. The file is an operator action: its directory must exist
  on the node and be writable by the service user.
- Bad: the boot check opens each rotation cup's worlds one at a time, so boot takes longer with more cups.

## Confirmation

`CupRotationTest` pins the rule. `FileCupRotationStoreTest` pins the state file's atomic write and its warnings.
`CupRotationDriverTest` pins the release-before-open order, the shared world, the deferred release and the fallback.
`RotationValidationTest` and `RotationBootCheckTest` pin the boot check over every cup of the rotation, and `RotationPlanTest`
the boot cup. The smoke boots of the change record the boot lines and the heap at `-Xmx1024M`.
