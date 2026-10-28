# Proposal

**Type and scope:** `feat(race)`. The change decides which cup a round plays, so it belongs to the race slice (`race.cup`,
`platform.cup`, `platform.lobby`). The configuration keys are read by the composition root, as every setting is. Ships as one
code change under the title `feat(race): play the next cup of the rotation after each cup`. Breaking: no. A server that sets
`VOYAGER_CUP` plays the same cup as before.

## Why

One Voyager game server plays exactly one cup. Two cups ship on `main` (`alpha_cup`, one map; `legacy_cup`, "Testing Cup",
three maps), and the owner wants the server to alternate between them: after each cup, the next round plays the next cup.
Today the cup is fixed by `VOYAGER_CUP` (or `-Pcup`) at boot, so changing it means changing the service's configuration.

The live CloudNet task `ElytraRace` runs one dynamic service on one node (mc01): `staticServices: false`,
`autoDeleteOnStop: true`, `minServiceCount: 1`, heap 1024 MB. Nothing stops the service after a cup. The playtest confirmed it:
the cup finished at 13:55:38, the player left ten seconds later, and the service kept running for over an hour. The service
restarts only on a deploy, and a restart starts from a fresh copy of the S3 template. So the rotation runs inside the JVM, and a
state file is needed only to keep the position across a deploy.

## What Changes

- A new setting `VOYAGER_CUP_ROTATION` names the cups of the rotation in play order, comma-separated. `VOYAGER_CUP` keeps its
  meaning as a rotation of one. Setting both refuses boot.
- When a cup finishes and its results have lingered, its racers are handed to the hub, and the next cup of the rotation is armed
  for the next round, wrapping to the first cup after the last. An aborted or restarted cup does not advance the rotation. This is
  the primary mechanism and it is a MUST.
- An optional state file (`VOYAGER_CUP_ROTATION_STATE`) keeps the armed cup across a deploy or restart. Example path:
  `/home/cloud/cloudnet/local/voyager-state/rotation`. It is a SHOULD. Without it the position lives in the JVM only.
- Only one cup's worlds are in heap at a time, with the lobby. Before the next cup's worlds load, the previous cup's worlds are
  released, except worlds both cups share. This is a MUST.
- The boot check validates the cups of the rotation one cup at a time. Definition errors (a malformed file, an unknown map, a
  duplicate name) refuse boot as they do for `VOYAGER_CUP` today. A cup whose worlds are missing is skipped with one WARN, and boot
  refuses only when no cup of the rotation is playable.
- Check mode (the validate-and-exit run) reports a skipped cup as a WARNING and exits 0 while a playable cup remains.
- The lobby sidebar and the hologram show the armed cup, the cup the next round will play. The tutorial book gains a sixth page,
  "Next cup", built from the same board when a racer opens it.

## Capabilities

### New Capabilities

- `cup-rotation`: the rotation list and its resolution, the arming of the next cup, the state file and its fallback, and the
  release of worlds when the cup changes.

### Modified Capabilities

- `cup-validation`: the boot check covers every cup of the rotation, one cup at a time. A missing world skips a cup with a WARN; a
  definition error still refuses. Check mode reports a skipped cup as a WARNING.
- `race/lobby-cup-display`: the lobby shows the cup the next round will play. Its requirement "The lobby shows the cup that is
  currently loaded" changes, because with a rotation the next cup is armed when the previous cup's results have lingered.
- `race/tutorial-book` (defined by `cygnus-style-lobby`, not yet archived): the book gains a sixth page for the armed cup.
- `server/configuration`: the two new keys are read through the loader and documented with the existing ones.

## Dependencies

- `add-catalogue-keys-and-authors` (PR #356, open). It changes cup and map identifiers to Adventure keys such as
  `elytrarace:legacy_cup`. This change refers to cups through the same resolution as `VOYAGER_CUP`, which accepts the flat id
  today and the key after #356. The state file stores the cup's canonical name, so a file written before #356 lands is still read
  after it. Implementation may start before #356 merges, but the rotation tests must cover both forms before it ships.
- `cygnus-style-lobby` (not archived). The tutorial-book delta is written against its requirement "The tutorial has five pages
  in a fixed order". If that change is archived first, the delta applies unchanged. This change cannot be archived before it:
  `openspec validate` reports that `race/tutorial-book` has no main spec yet, the same position
  `refine-lobby-countdown-feedback` is in.
- PR #352 (`fix(race)`, open) edits `WaitingRoom`, the class that draws the lobby displays. The implementation must rebase on it
  or wait for it.

## Rollout context (not part of this change)

- The live rotation is `alpha_cup`, then `legacy_cup`, once the legacy worlds are on S3. Today the S3 template holds only
  `ElytraLobby` and `ElytraraceBlueAndRed`. `PalmBeach`, `DragonLobby` and `Memoria` (about 220 MB) sit in
  `local:ElytraRace/legacy-1.13.0` and still have to be copied to S3. Until then `legacy_cup` is skipped with a WARN at boot, and
  every round plays `alpha_cup`.
- The operator sets `VOYAGER_CUP_ROTATION`, and `VOYAGER_CUP_ROTATION_STATE` with the directory
  `/home/cloud/cloudnet/local/voyager-state/`, in the task JSON. That directory does not exist yet. The task change is an
  operator action, not part of this pull request.

## Out of scope

- Choosing the hub, the fallback and the proxy: unchanged (`cloudnet/hub-handoff`).
- Any change to `add-cloudnet-deployment`, the bridge, the CloudNet task, its templates or the S3 template. The repository copy
  `docs/deployment/cloudnet-task.json` still shows `minServiceCount` 0 and 512 MB; the live values are the ones above, and that
  file is not changed here.
- Persisting the rotation in PostgreSQL. Statistics are optional (`VOYAGER_PERSISTENCE`), and the rotation must not depend on them.
- Weighted, random or time-based rotations. The owner asked for the next cup, in order.
- Changing what a round plays: rings, scoring, maps, the cup's mode, the race timings and the standings screen.
- Changing the data of `legacy_cup` or `alpha_cup`, or copying the legacy worlds to S3.
- Archiving `rotate-cups-per-round` and ticking its tasks, which is a follow-up PR the owner asks for.
