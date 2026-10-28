# Proposal

**Type and scope:** `feat(race)`. The change decides which cup a round plays, so it belongs to the race slice (`race.cup`,
`platform.cup`, `platform.lobby`). The configuration keys are read by the composition root, as every setting is. Ships as one
code change under the title `feat(race): play the next cup of the rotation after each cup`. Breaking: no. A server that sets
`VOYAGER_CUP` plays the same cup as before.

## Why

One Voyager game server plays exactly one cup. Two cups ship on `main` (`alpha_cup`, one map; `legacy_cup`, "Testing Cup",
three maps), and the owner wants the server to alternate between them: after each cup, the next round plays the next cup.
Today the cup is fixed by `VOYAGER_CUP` (or `-Pcup`) at boot, so changing it means changing the service's configuration.

The CloudNet task `ElytraRace` is dynamic according to the repository's deploy notes (`autoDeleteOnStop: true`, design
`staticServices: false`; the live task file is not in the repository): the service directory is deleted when a service stops
and copied again from the template on the next start. Nothing survives a restart inside the JVM, so the rotation needs a
decision on where its state lives. That decision is the core of this proposal (see `design.md`).

## What Changes

- A new setting `VOYAGER_CUP_ROTATION` names the cups of the rotation in play order, comma-separated. `VOYAGER_CUP` keeps its
  meaning as a rotation of one. Setting both refuses boot.
- A cup finishes, and the next round plays the next cup of the rotation, wrapping to the first. An aborted or restarted cup
  does not advance the rotation.
- An optional state file (`VOYAGER_CUP_ROTATION_STATE`) outside the service directory keeps the upcoming cup across a restart.
  Without it the rotation is remembered in the JVM only, and a restart begins again at the first cup.
- The boot check validates every cup named in the rotation: definition errors (a malformed file, an unknown map, a duplicate
  name) refuse boot as they do for `VOYAGER_CUP` today. A cup whose worlds are missing is skipped with one WARN, and boot
  refuses only when no cup of the rotation is playable.
- The lobby sidebar and the hologram show the upcoming cup, the cup the next round will play. The tutorial book gains a sixth
  page, "Next cup", built from the same board when a racer opens it.
- The worlds of the upcoming cup are opened before its round, and the worlds of cups that are neither running nor upcoming are
  released. A server with little heap (the live task has 512 MB) never holds the worlds of every cup at once.

## Capabilities

### New Capabilities

- `cup-rotation`: the rotation list and its resolution, the advance on a finished cup, the state file and its fallback, and the
  world handling of the upcoming cup.

### Modified Capabilities

- `cup-validation`: the boot check covers every cup of the rotation, not only the played one. A missing world skips a cup with a
  WARN; a definition error still refuses.
- `race/lobby-cup-display`: the lobby shows the cup the next round will play. Its requirement "The lobby shows the cup that is
  currently loaded" changes, because with a rotation the next cup is known as soon as the previous one finishes.
- `race/tutorial-book` (defined by `cygnus-style-lobby`, not yet archived): the book gains a sixth page for the upcoming cup.
- `server/configuration`: the two new keys are read through the loader and listed with the existing ones.

## Dependencies

- `add-catalogue-keys-and-authors` (PR #356, open). It changes cup and map identifiers to Adventure keys such as
  `elytrarace:legacy_cup`. This change refers to cups through the same resolution as `VOYAGER_CUP`, which accepts the flat id
  today and the key after #356. The state file stores the cup's canonical name, so a file written before #356 lands is still
  read after it. Implementation may start before #356 merges, but the rotation tests must cover both forms before it ships.
- `cygnus-style-lobby` (not archived). The tutorial-book delta is written against its requirement "The tutorial has five
  pages in a fixed order". If that change is archived first, the delta applies unchanged. This change cannot be archived before it: `openspec validate`
reports that `race/tutorial-book` has no main spec yet, the same position `refine-lobby-countdown-feedback` is in.
- PR #352 (`fix(race)`, open) edits `WaitingRoom`, the class that draws the lobby displays. The implementation must rebase on it
  or wait for it.

## Out of scope

- Choosing the hub, the fallback and the proxy: unchanged (`cloudnet/hub-handoff`).
- Any change to `add-cloudnet-deployment`, the bridge, the CloudNet task or its templates. The task file is not changed by this
  proposal; the operator sets the new keys in the task environment.
- Persisting the rotation in PostgreSQL. Statistics are optional (`VOYAGER_PERSISTENCE`), and the rotation must not depend on them.
- Weighted, random or time-based rotations. The owner asked for the next cup, in order.
- Changing what a round plays: rings, scoring, maps, the cup's mode, the race timings and the standings screen.
- Changing `legacy_cup`'s or `alpha_cup`'s data, or shipping the missing worlds of `legacy_cup`.
- Archiving `rotate-cups-per-round`, ticking its tasks, and deciding ADR-0041 (proposed here, accepted by the owner later).

## Open questions for the owner

These are answered in `design.md`, section "Open questions". The recommendation depends on the first two.

1. What stops the service after a cup? The repository does not: the game server exits only on `stop` from the console, `/stop`
   with the node permission, or the stop command of the node.
2. Does the live node have a directory that survives a service's stop and is outside the service directory, and may the task
   environment point at it?
3. Which cups are in the live rotation, and do their worlds exist on the live node? The dev checkout has only
   `ElytraraceBlueAndRed`.
