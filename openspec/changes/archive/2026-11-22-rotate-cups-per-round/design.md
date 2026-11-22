# Design

## Context

### Live facts (checked read-only on mc01)

- The task `ElytraRace` is dynamic: `staticServices: false`, `autoDeleteOnStop: true`, `minServiceCount: 1`, heap 1024 MB, one
  node (mc01).
- Every stop wipes the service directory. The next start is a fresh copy of the S3 template.
- Nothing stops the service after a cup. There is no idle or empty-server rule, and no CloudNet module beyond Bridge,
  Database-MySQL, Rest, Signs, Storage-S3 and SyncProxy is running. The playtest confirms it: the cup finished at 13:55:38, the
  player left ten seconds later, and the service ran for over an hour. The stop and start lines in chat were deploy restarts.
- A node-local directory `/home/cloud/cloudnet/local/voyager-state/` is possible. The service user `cloud` can write to it, and it
  survives stops. It does not exist yet.
- The S3 template holds `ElytraLobby` and `ElytraraceBlueAndRed`. `PalmBeach`, `DragonLobby` and `Memoria` are in
  `local:ElytraRace/legacy-1.13.0` and still need copying to S3. That copy is out of scope.

### How the cup is selected today

- The setting is `VOYAGER_CUP`. It is also the Gradle property `-Pcup`. The key is `ServerSettings.CUP_PROPERTY`
  (`voyager/server/.../config/ServerSettings.java`). Every setting is read through `ConfigLoader` (avaje-config, ADR-0036,
  `voyager/platform/.../config/ConfigLoader.java`), so the environment variable `VOYAGER_CUP` works too. A blank value counts as
  absent.
- `ServerSettings.cupName` is `Optional<String>`. Absent means "the catalogue's only cup".
- `CupResolution.resolve(reading, chosen)` (`voyager/platform/.../catalog/CupResolution.java`) picks the cup. A name resolves to
  that cup. With no name, the cup directory must hold exactly one cup file; more than one refuses as ambiguous, naming
  `-Pcup=` and `-DVOYAGER_CUP=`.
- `CatalogReloader.loadInitial` resolves the boot cup once and logs one WARN for the cups that are not played
  (`CupResolution.skippedCups`). `CatalogHolder` holds the one `LoadedCatalog` (snapshot plus cup). `ServerBeans` builds it.

### The boot check today

- `ConfigCheck.bootRefusals` (`voyager/server/.../config/ConfigCheck.java`) refuses on the catalogue's structural errors, the
  cup selection, and the worlds of the played cup's maps only (`cup.mapNames().contains(map.name())`). Other cups' worlds are
  never opened. This was commit 84a5675: opening every world at boot ran a 1 GB JVM out of heap.
- After the graph is built, `VoyagerServer` calls `openEveryWorld(catalog.current().rotation(), instances)`. That opens the
  played cup's worlds, and no others.
- A missing world folder for the played cup is an ERROR, and boot refuses (`CatalogValidation`).

### Where the rotation runs

- A round ends and the server stays up. `WaitingRoom` hands every racer online to the hub after `RESULTS_LINGER` (ADR-0033),
  and then the room keeps waiting in the same JVM. Voyager has no exit path after a cup: the only stop paths are
  `ServiceShutdown` (stdin `stop`, `/stop` with `voyager.command.stop`, the console).
- `WaitingRoom.announce` builds `LobbyBoard.of(catalog.current().cup(), ...)` on every tick. The sidebar and the hologram both
  read that board. The hologram is spawned once at boot from the boot cup (`ServerBeans`) and corrected on the first tick.
- `CatalogHolder.current()` changes only in `promoteForNewRound()`, which `CupSession.start` calls at a round start. A reload
  waits in `pending()` until then.
- `TutorialBook` builds its five pages once, when the book is constructed (`TutorialPages.COUNT = 5`). None of them names a cup.
- `MapInstances` already has `open(world)`, `discard(world)`, `isOpen(world)` and `checkWorld(world)`, which reads, reports and
  closes a world.

### Shipped cups and the dev checkout

| Cup | Name in the file | Maps | World folders |
|---|---|---|---|
| `alpha_cup` | `alpha_cup` | `elytraraceblueandred` | `ElytraraceBlueAndRed` |
| `legacy_cup` | `legacy_cup` ("Testing Cup") | `palmbeach`, `dragonlobby`, `memoria` | `PalmBeach`, `DragonLobby`, `Memoria` |

The dev checkout's `run/run/worlds` holds only `ElytraraceBlueAndRed`. Locally, and on the live node until the legacy worlds reach
S3, `legacy_cup` is therefore missing a world. The missing-world policy below covers that case.

### Dependency on `add-catalogue-keys-and-authors` (PR #356, open)

#356 changes cup and map identifiers to Adventure keys in the `elytrarace` namespace (`elytrarace:legacy_cup`). It keeps the flat
id readable and makes `VOYAGER_CUP` accept both forms. This design refers to a cup only through the resolution rule that
`VOYAGER_CUP` already uses. Each entry of `VOYAGER_CUP_ROTATION` is resolved that way, so the same list works before and after
#356. The state file stores the cup's canonical name, which is the key after #356 and the flat id before it. A state file written
before #356 is mapped by the same resolution after it.

## Options for the rotation position

The brief's options are (a), (b) and (c). (d) is listed for completeness.

### (c) Stay up and rotate in the JVM (primary, MUST)

The server stays up after a cup, as it does today. When a cup finishes and its results have lingered, its racers are handed to
the hub, the next cup of the rotation is armed, and the next round plays it. The position is an index in the JVM. It needs no
file, no database and no node change.

The live facts make this safe. Nothing stops the service after a cup (see "Live facts"), and the service restarts only on a
deploy. Within one JVM the position is always right. A deploy starts from the first cup, and the state file (below) covers that.

### (a') A state file outside the service directory (SHOULD)

The state file's path comes from `VOYAGER_CUP_ROTATION_STATE`. The example path is
`/home/cloud/cloudnet/local/voyager-state/rotation`. The service writes the canonical name of the armed cup when the rotation
advances, through a temporary file and a move, and reads it at boot.

- It survives a deploy, a restart and a fresh copy of the template, because the directory is outside the service directory and the
  node does not clear it.
- It needs one directory on the node and one environment variable in the task JSON. The directory is an operator action.
- It does not depend on statistics.
- Failure modes: a write that fails logs one WARN and the rotation continues in the JVM. A file that cannot be read, or that names
  a cup not in the rotation, starts at the first cup with one WARN that names the file. A crash between "cup finished" and "file
  written" repeats one cup, which is acceptable.
- One state file serves one service. The owner runs one ElytraRace service.

### (a) A counter in a file in the service's working directory (ruled out)

The file lives in the service directory, which is wiped on every stop (`autoDeleteOnStop: true`), so it is gone on the next
start. The greenfield design also rules out a race server inheriting the previous match's directory.

### (b) A deterministic choice without state (rejected)

- Seeded random, seed = start time: not a rotation. Two rounds can play the same cup back to back.
- Time slots, index = floor(now / slot) mod n: a round does not map to a slot, so "the next cup after this one" is not guaranteed.

### (d) The last finished cup from PostgreSQL (not needed)

`cup_session.cup_key` records every finished cup. This would depend on `VOYAGER_PERSISTENCE=on` and on the database at boot, which
the rotation does not need now that the state file exists.

### Recommendation

Option (c) as the mechanism, and option (a') as the SHOULD that keeps the position across a deploy. Without the state file the
server warns once at boot that a restart begins again at the first cup.

## Rotation semantics

- `VOYAGER_CUP_ROTATION` is an ordered, comma-separated list of cup identifiers. Blank entries and surrounding spaces are dropped.
  An empty list, an identifier that resolves to no cup, or a duplicate refuses boot, as a bad named selection does today.
- The list is the rotation. `VOYAGER_CUP` alone is a rotation of one, with the current rules: one cup file plays alone, and more
  than one refuses as ambiguous. Setting both keys refuses boot and names both.
- `armed` is the cup the next round plays. At boot it is the first cup of the list, or the state file's cup if that cup is in the
  list and playable.
- A cup finishes, its results linger, and its racers are handed to the hub. At that point the next cup of the rotation is armed:
  the previous cup's worlds are released and the armed cup's worlds are opened (see "Worlds"). The state file is written. The
  next round pins the armed cup through `CatalogHolder.promoteForNewRound()` (unchanged) and resolves it against the pinned
  snapshot.
- "The next start" means the next round in the same JVM. A round start never moves the rotation.
- An aborted cup (for example, the last racer leaves) and a restarted cup (`/race restart`) do not advance the rotation. The
  armed cup is replayed.
- A running round is never changed. A reload applies from the next round, as today.

## Boot-time world check for every cup in the rotation

- Scope. Each cup named by the rotation is validated. Structural checks already cover the whole catalogue.
- One cup at a time. The worlds of one cup are checked, and then released, before the next cup's worlds are checked. Within a cup
  each world is read and closed through `MapInstances.checkWorld` before the next one opens. This keeps the boot's heap to one
  cup's worlds plus the lobby. The memory note from commit 84a5675 applies: never hold every cup's chunks at once.
- Cost. Boot time grows with the number of cups. The check mode and the smoke test measure it.
- Definition errors refuse boot, for every cup of the rotation. These are a malformed file, a missing `mode`, a duplicate name, a
  map the cup names that no map file provides, and a name that resolves to no cup. An operator who puts a cup in the list has
  asked for it, so a broken definition is refused as a named selection is refused today.
- Missing worlds skip the cup. A missing or empty world folder for a cup of the rotation is a deployment condition, not a
  definition error. The cup is skipped for this boot with one WARN that names it and its missing world. If no cup of the rotation
  is playable, boot refuses with the existing message, listing each cup and its problem.
- Damaged worlds refuse. A world that is present but unreadable (an unreadable region, chunk errors) keeps today's ERROR and
  refuses boot. Only absence is a skip, so a damaged world is never played silently.
- The state file's cup is checked the same way. If it is skipped, the armed cup moves to the next playable cup of the list.
- Check mode (`ConfigCheck.run`, validate-and-exit) reports a skipped cup as a WARNING and exits 0 while at least one cup is
  playable. Refusals still exit 1.

## Worlds while the rotation runs

- Only one cup's worlds are in heap at a time, together with the lobby world. This is a MUST.
- When the armed cup changes, the previous cup's worlds are released with `MapInstances.discard` before the armed cup's worlds
  are opened. A world that both cups share is kept. Racers are handed to the hub first, so nobody stands in a released world.
- A world of the armed cup that fails to open is logged as ERROR. The rotation moves to the next playable cup. If no cup can be
  opened, the server keeps the last cup and logs ERROR. It never stops a running round.
- The opening happens on the cup-end side of the lobby, not on a player's tick, so the lobby is already in place when it runs.
- Peak heap at 1024 MB: the lobby plus the largest single cup of the rotation. The smoke test checks it.

## Lobby

- The lobby shows the armed cup, the cup the next round will play. `race/lobby-cup-display` changes so that the requirement "The
  lobby shows the cup that is currently loaded" becomes "The lobby shows the cup the next round will play". With no rotation the
  behaviour is unchanged.
- The armed cup is shown from the moment it is armed, which is when the previous cup's results have lingered and its racers have been
  handed to the hub. The results screen itself is unchanged.
- The board is built from the armed cup and the snapshot the next round will pin. A reload still waits for the next round. The
  rotation decides which cup is next; a reload does not change that order.
- The sidebar and the hologram read the same board. The hologram is spawned at boot from the armed cup, and corrected on every
  tick as before.
- A countdown that is running keeps the cup it began with. The armed cup changes only after a finish, never during a countdown.

## Tutorial book

- The owner decided: a sixth page, "Next cup", follows the five pages. It shows the armed cup's display name and its maps in play
  order, with the sidebar's rule: at most `LobbyBoard.MAX_MAP_LINES` lines, and a "more" line for the rest.
- The page is built from the board when the racer opens the book. Pages one to five are still built once. The book stores
  nothing, so "The tutorial stores nothing" stays as it is.
- Without a rotation the page names the one cup the server plays.
- Both locales get the new keys: `voyager_en_US.properties` and `voyager_de_DE.properties`, with informal "du" in German.
  `MessageBundleParityTest` enforces the parity.

## Dependency direction and modules

Dependencies point inward: `voyager-server` (composition root) to `voyager-platform` to `voyager-race` to `voyager-api`.

| Type | Module and package | Why there |
|---|---|---|
| `CupRotation` (record: ordered cup names, position, `armed()`, `advance()`, wrap) | `voyager-race`, `net.elytrarace.voyager.race.cup.rotation` (new package, `package-info` with `@NotNullByDefault`) | A pure rule: no clock, no I/O. Tested without Minestom. |
| `CupRotationStore` (port: `Optional<String> read()`, `void write(String)`) | `voyager-race`, same package | The race slice owns the rule, so it owns the port the rule needs. |
| `FileCupRotationStore` (adapter: temporary file and move, WARN on failure) | `voyager-platform`, `net.elytrarace.voyager.platform.rotation` (new package, `package-info`) | Infrastructure, next to `platform.catalog` and `platform.world`. It reads no environment. |
| `CupRotationSettings` (parses `VOYAGER_CUP_ROTATION` and `VOYAGER_CUP_ROTATION_STATE`; refuses both keys and an empty list) | `voyager-server`, `net.elytrarace.voyager.server.config` | Settings live in the composition root's config package, next to `ServerSettings`. |
| Lobby board and tutorial page | `voyager-platform`, `platform.lobby` and `platform.tutorial` | Adapters; no race types beyond those they already use. |

Fitness:

- `race.cup.rotation` may not use the configuration library. `ConfigurationBoundaryTest.raceDoesNotDependOnConfigLibrary` covers
  it without a change.
- Only the composition root reads the environment (`environmentAccessIsConfinedToTheEdge`). The store takes a `Path` from the root.
- `NullabilityConventionTest` (rule 5) requires `package-info.java` with `@NotNullByDefault` in each new package. The test already
  exists and needs no change.
- `SliceBoundaryRulesTest` r3 covers only `platform.text`, `convert`, `world`, `tick` and `render`. The new platform package may
  depend on `race`, so `FileCupRotationStore` may implement the port.
- No `voyager-fitness` rule is added. No new module.

## Architecture decision

ADR-0041, "Cup rotation: in-process, with an optional state file outside the service directory", is written in `docs/decisions/`
with the code change and stays Proposed until the owner accepts it. The number is 0041 because 0040 belongs to the open #356.

## Greenfield design

The change does not alter any decision of `docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`. Its CloudNet section says
a race server "must never inherit the previous match's directory". The state file lives outside that directory, and the in-process
rotation needs no directory at all.

## Risks

- Peak heap. Under the live 1024 MB heap, the lobby plus the largest cup must fit. The smoke test measures it. If it does not fit,
  the owner decides between a smaller cup and a larger heap before the code PR merges.
- Boot time grows with the number of cups in the rotation. The smoke test records it.
- A world released while a racer still stands in it. Mitigated by releasing only after the hand-off.
- #352 (`fix(race)`, open) edits `WaitingRoom`. The implementation rebases on it.
- The state file's directory must be writable by the `cloud` user. The boot WARN names the path, and the configuration check refuses
  a path whose parent directory does not exist.
- The repository copy `docs/deployment/cloudnet-task.json` shows `minServiceCount` 0 and 512 MB. The live task differs. This change
  does not edit that file.

## Settled decisions

These replace the earlier open questions. The owner settled them with the live facts above.

- Nothing stops the service after a cup. The rotation runs in the JVM (option c).
- The task is dynamic (`staticServices: false`, `autoDeleteOnStop: true`), with `minServiceCount: 1` and a 1024 MB heap.
- The persistent directory is `/home/cloud/cloudnet/local/voyager-state/`. The state file is a SHOULD, and its example path is
  `/home/cloud/cloudnet/local/voyager-state/rotation`.
- An aborted cup does not advance the rotation.
- Check mode reports a skipped cup as a WARNING and exits 0 while a playable cup remains.
- "The next start" means the next round in the same JVM.
- The tutorial book gets the sixth page "Next cup".
- One state file serves one service.

## Rollout context (not part of this change)

- The live rotation is `alpha_cup`, then `legacy_cup`, once the legacy worlds are on S3. Until then `legacy_cup` is skipped with a
  WARN at boot, and every round plays `alpha_cup`.
- The operator sets `VOYAGER_CUP_ROTATION`, and `VOYAGER_CUP_ROTATION_STATE=/home/cloud/cloudnet/local/voyager-state/rotation`, in
  the task JSON, and creates the directory on mc01. Both are operator actions.
