# Design

## Context

### How the cup is selected today

- The setting is `VOYAGER_CUP`. It is also the Gradle property `-Pcup`, which the run tasks pass as the system property. The
  key is `ServerSettings.CUP_PROPERTY` (`voyager/server/.../config/ServerSettings.java`). Every setting is read through
  `ConfigLoader` (avaje-config, ADR-0036, `voyager/platform/.../config/ConfigLoader.java`), so the environment variable
  `VOYAGER_CUP` works too. A blank value counts as absent.
- `ServerSettings.cupName` is `Optional<String>`. Absent means "the catalogue's only cup".
- `CupResolution.resolve(reading, chosen)` (`voyager/platform/.../catalog/CupResolution.java`) picks the cup. A name resolves
  to that cup. With no name, the cup directory must hold exactly one cup file; more than one refuses as ambiguous, naming
  `-Pcup=` and `-DVOYAGER_CUP=`.
- `CatalogReloader.loadInitial` resolves the played cup once at boot and logs one WARN for the cups that are not played
  (`CupResolution.skippedCups`). `CatalogHolder` holds the one `LoadedCatalog` (snapshot plus cup). `ServerBeans` builds it.

### The boot check today

- `ConfigCheck.bootRefusals` (`voyager/server/.../config/ConfigCheck.java`) refuses on the catalogue's structural errors, the
  cup selection, and the worlds of the played cup's maps only. The predicate is `cup.mapNames().contains(map.name())`. Other
  cups' worlds are never opened. This was commit 84a5675: opening every world at boot ran a 1 GB JVM out of heap.
- After the graph is built, `VoyagerServer` calls `openEveryWorld(catalog.current().rotation(), instances)`. That opens the
  played cup's worlds, and no others.
- A missing or empty world folder for the played cup is an ERROR, and boot refuses (`CatalogValidation`).

### What survives a restart

Nothing in the JVM survives. The service is a dynamic CloudNet service: `docs/deployment/cloudnet-task.json` has
`autoDeleteOnStop: true`, and the greenfield design (`docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`,
section "CloudNet v4") says the task runs `staticServices: false` and that a race server "must never inherit the previous
match's directory". On stop the service directory is removed. On start it is built again from the template `ElytraRace/default`.
The live task file is not in the repository; the repository copy and the design both say dynamic.

### What ends a service after a cup

Voyager does not end its own process after a cup. The only stop paths in the code are `ServiceShutdown`, which runs on the
stdin line `stop`, on `/stop` by a holder of `voyager.command.stop`, and on the console. A finished cup hands its racers to the
hub after `RESULTS_LINGER` (`WaitingRoom`, ADR-0033), and then the room keeps waiting in the same JVM. So the stop the owner
describes comes from outside the code: the node's automation or an operator. That is question Q1 below.

### The lobby and the tutorial today

- `WaitingRoom.announce` builds `LobbyBoard.of(catalog.current().cup(), ...)` on every tick. The sidebar and the hologram both
  read that board. The hologram is spawned once at boot from the boot cup (`ServerBeans`), and corrected on the first tick.
- `CatalogHolder.current()` changes only in `promoteForNewRound()`, which `CupSession.start` calls at a round start. A reload
  waits in `pending()` until then. `race/lobby-cup-display` states this as "the lobby shows the cup that is currently loaded".
  With a rotation the next cup is known as soon as the previous one finishes, so that rule has to change (see "Lobby").
- `TutorialBook` builds its five pages once, when the book is constructed (`TutorialPages.COUNT = 5`). None of them names a cup.
- `MapInstances` already has `open(world)`, `discard(world)`, `isOpen(world)` and `checkWorld(world)`, which reads, reports and
  closes a world. The rotation can use these directly.

### Shipped cups and the dev checkout

| Cup | Name in the file | Maps | World folders |
|---|---|---|---|
| `alpha_cup` | `alpha_cup` | `elytraraceblueandred` | `ElytraraceBlueAndRed` |
| `legacy_cup` | `legacy_cup` ("Testing Cup") | `palmbeach`, `dragonlobby`, `memoria` | `PalmBeach`, `DragonLobby`, `Memoria` |

The dev checkout's `run/run/worlds` holds only `ElytraraceBlueAndRed`. A local rotation of both cups therefore has
`legacy_cup` missing. This is the case the missing-world policy below is for, and the smoke test uses it.

### Dependency on `add-catalogue-keys-and-authors` (PR #356, open)

#356 changes cup and map identifiers to Adventure keys in the `elytrarace` namespace (`elytrarace:legacy_cup`). It keeps the
flat id readable and makes `VOYAGER_CUP` accept both forms. This design refers to a cup only through the resolution rule that
`VOYAGER_CUP` already uses. Each entry of `VOYAGER_CUP_ROTATION` is resolved that way, so the same list works before and
after #356. The state file stores the cup's canonical name, which is the key after #356 and the flat id before it. A state
file written before #356 is mapped by the same resolution after it.

## Options for the rotation state

The brief's options are (a), (b) and (c). Each is evaluated against the facts above. A fourth, (d), is listed for completeness.

### (a) A counter in a file in the service's working directory

Ruled out. The file lives in the service directory, and that directory is deleted on stop (`autoDeleteOnStop: true`). The file
is gone on the next start. It would survive only in a static service with `autoDeleteOnStop: false`, which the greenfield
design rules out ("never inherit the previous match's directory").

### (a') The same counter in a file outside the service directory (recommended)

The state file's path comes from `VOYAGER_CUP_ROTATION_STATE`, set in the task environment, and points at a directory the node
does not clear. The file holds the canonical name of the cup the next round plays. The service writes it when a cup finishes,
through a temporary file and a move, and reads it at boot.

- It survives a stop, a restart, a fresh template copy and a crash. Restart by CloudNet, restart by an operator, and no
  restart at all all give the same answer, so the open question Q1 does not block it.
- It needs no change to CloudNet, the bridge, the template or any network service. The node supplies one writable directory,
  and the operator adds one environment variable to the task.
- It does not depend on statistics. `VOYAGER_PERSISTENCE` can stay off.
- Failure modes. A write that fails logs a WARN and keeps the rotation in memory for this JVM. A file that names a cup that is
  not in the list, or that cannot be read, starts at the first cup with a WARN naming the file. A crash between "cup finished"
  and "file written" repeats one cup, which is acceptable.
- Several services that share one file advance one shared rotation. The owner has one ElytraRace service today, so this is
  fine, and it is the behaviour a shared rotation would want.

### (b) A list with a deterministic choice that needs no state

- Seeded random, seed = start time: not a rotation. Two rounds can play the same cup back to back, and nothing gives "the
  next one". Rejected.
- Time slots, index = floor(now / slot) mod n: stateless, and the same across restarts, but a round does not map to a slot. A
  cup can run across a slot boundary, and two rounds can fall into the same slot. "The next cup after this one" is not
  guaranteed. Rejected unless the owner accepts time-based choice.

### (c) Stay up between cups, with the rotation in memory

Voyager already stays up after a cup (see "What ends a service after a cup"). The rotation can be an index in the JVM that
advances when a cup finishes and is read at each round start. It needs no file and no database, and it is the smallest change.

Its weakness is the owner's own description of the live setup. If the node stops the service after each cup and CloudNet
starts a new one from the template, the index is lost every time, and the server plays the first cup forever. The failure is
silent. Making (c) safe means the node must not stop the service between cups, which the owner has said stays as it is.

### (d) Ask PostgreSQL for the last finished cup (not recommended now)

`cup_session.cup_key` already records every finished cup, so the next cup could be the one after the last row. This survives
restarts, but the rotation would depend on `VOYAGER_PERSISTENCE=on` and on the database being reachable at boot, and a
switched-off statistics setting would silently turn the rotation off. It can be a later step if (a') is refused.

### Recommendation

Option (a'). It works whatever the answer to Q1, needs one directory and one environment variable on the node, and keeps the
statistics and the CloudNet setup out of the rotation. Option (c) is the fallback when `VOYAGER_CUP_ROTATION_STATE` is not set,
and the server warns once at boot when a rotation of more than one cup runs without a state file.

## Rotation semantics

- `VOYAGER_CUP_ROTATION` is an ordered, comma-separated list of cup identifiers. Blank entries and surrounding spaces are
  dropped. An empty list, an identifier that resolves to no cup, or a duplicate refuses boot, as a bad named selection does
  today.
- The list is the rotation. `VOYAGER_CUP` alone is a rotation of one, and it keeps the current rules: one cup file plays alone,
  and more than one refuses as ambiguous. Setting both keys refuses boot and names both.
- `upcoming` is the position of the next round. It starts at the first cup of the list, or at the state file's cup if that
  cup is in the list.
- A round start pins the upcoming cup (through `CatalogHolder.promoteForNewRound()`, unchanged) and resolves it against the
  pinned snapshot. `CupSession` keeps the pinned cup as it does today.
- A cup that finishes advances `upcoming` by one and wraps. A cup that is aborted (for example, the last racer leaves) or
  restarted (`/race restart`) does not advance it. The recommendation is a replay of the same cup. Q4 asks the owner.
- The state file is written on each advance. A WARN logged at boot names the path when the rotation has more than one cup and
  no state file is set.

## Boot-time world check for every cup in the rotation

- Scope. The check covers the union of the maps of every cup named in the rotation. Structural checks are unchanged: they
  already cover the whole catalogue.
- Memory. Worlds are checked one at a time through `MapInstances.checkWorld`, which reads and closes each world before the next
  one opens. The bound is the same as for the played cup today (one world, one region at a time), and the rotation adds no
  resident world.
- Cost. Boot time grows with the number of distinct worlds. The shipped rotation has four (`ElytraraceBlueAndRed`, `PalmBeach`,
  `DragonLobby`, `Memoria`). The smoke test measures it under `-Xmx512M`, the live heap.
- Definition errors refuse boot, for every cup of the rotation. These are a malformed file, a missing `mode`, a duplicate name,
  a map the cup names that no map file provides, and a name that resolves to no cup. An operator who puts a cup in the list
  has asked for it, so a broken definition is refused as the named selection is today.
- Missing worlds skip the cup. A missing or empty world folder for a cup of the rotation is a deployment condition, not a
  definition error. The cup is skipped for this boot with one WARN that names it and its missing world. If no cup of the rotation
  is playable, boot refuses with the existing message, listing each cup and its problem.
- Damaged worlds refuse. A world that is present but unreadable (an unreadable region, chunk errors) keeps today's ERROR and
  refuses boot, as it does for the played cup now. Only absence is a skip, so a damaged world is never played silently.
- The state file's cup is checked the same way. If it is skipped, the upcoming cup moves to the next playable cup of the list.
- Check mode (`ConfigCheck.run`, validate-and-exit) reports a skipped cup as a WARNING and exits 0 when at least one cup is
  playable. This is a change to `server/config-check-mode`. Q5 asks the owner to confirm.

## Worlds while the rotation runs

- A cup's worlds are open before its round can start. When a cup finishes and the upcoming cup changes, its worlds are opened
  with `MapInstances.open`. A world that fails to open is logged as ERROR, and the rotation moves on to the next playable cup.
- The worlds of cups that are neither running nor upcoming are released with `discard`. At most two cups are open: the running
  one and the upcoming one. With `legacy_cup` (three worlds) and `alpha_cup` (one), that is four worlds at the peak.
- A failed open never stops a running round. It happens on the lobby side of the cup end, not on a tick.

## Lobby

- When a rotation is configured, the lobby shows the upcoming cup: the cup the next round will play. `race/lobby-cup-display`
  changes so that the requirement "The lobby shows the cup that is currently loaded" becomes "The lobby shows the cup the next
  round will play". With no rotation the behaviour is exactly the current one.
- The board is built from the upcoming cup and the snapshot the next round will pin. A reload still waits for the next round:
  it can change the cup's maps or name only from that round on. The rotation decides which cup is next; a reload does not
  change that order.
- The sidebar and the hologram read the same board, as they do today. The hologram is spawned at boot from the upcoming cup, so
  its first text is right, and it is corrected on every tick as before.
- A countdown that is running keeps the cup it began with. The rotation changes the upcoming cup only when a cup finishes, which
  is never during a countdown.

## Tutorial book

- A sixth page, "Next cup", is added after the five pages. It shows the upcoming cup's display name and its maps in play order,
  with the same rule as the sidebar: at most `LobbyBoard.MAX_MAP_LINES` lines, and a "more" line for the rest.
- The page is built from the board when the racer opens the book. Pages one to five are still built once. The book stores
  nothing, so the requirement "The tutorial stores nothing" stays as it is.
- Without a rotation the page shows the one cup the server plays.
- Both locales get the new keys: `voyager_en_US.properties` and `voyager_de_DE.properties`, with informal "du" in German.
  `MessageBundleParityTest` enforces the parity.
- Alternative: leave the book alone and show the upcoming cup only on the sidebar and the hologram. That needs no change to the
  tutorial's five-page requirement. Q9 asks the owner.

## Dependency direction and modules

Dependencies point inward: `voyager-server` (composition root) to `voyager-platform` to `voyager-race` to `voyager-api`.

| Type | Module and package | Why there |
|---|---|---|
| `CupRotation` (record: ordered cup names, position, `upcoming()`, `advance()`, wrap) | `voyager-race`, `net.elytrarace.voyager.race.cup.rotation` (new package, `package-info` with `@NotNullByDefault`) | A pure rule, no clock, no I/O. Tested without Minestom. |
| `CupRotationStore` (port: `Optional<String> read()`, `void write(String)`) | `voyager-race`, same package | The race slice owns the rule, so it owns the port the rule needs. |
| `FileCupRotationStore` (adapter: temporary file and move, WARN on failure) | `voyager-platform`, `net.elytrarace.voyager.platform.rotation` (new package, `package-info`) | Infrastructure, next to `platform.catalog` and `platform.world`. It reads no environment. |
| `CupRotationSettings` (parses `VOYAGER_CUP_ROTATION`, `VOYAGER_CUP_ROTATION_STATE`; refuses both-keys and empty) | `voyager-server`, `net.elytrarace.voyager.server.config` | Settings live in the composition root's config package, next to `ServerSettings`. |
| Lobby board and tutorial page | `voyager-platform`, `platform.lobby` and `platform.tutorial` | Adapters; no race types beyond those they already use. |

Fitness.

- `race.cup.rotation` may not use the configuration library. `ConfigurationBoundaryTest.raceDoesNotDependOnConfigLibrary` covers
  it without a change.
- Only the composition root reads the environment (`environmentAccessIsConfinedToTheEdge`). The store takes a `Path` from the
  root.
- `NullabilityConventionTest` (rule 5) requires `package-info.java` with `@NotNullByDefault` in each new package. This is a
  test that already exists and needs no change.
- `SliceBoundaryRulesTest` r3 covers only `platform.text`, `convert`, `world`, `tick` and `render`. The new platform package may
  depend on `race`, so `FileCupRotationStore` may implement the port.
- No `voyager-fitness` rule is added. No new module.

## Architecture decision

ADR-0041, "Cup rotation state lives outside the service directory", is written in `docs/decisions/` with the code change and
stays Proposed until the owner accepts it. The number is 0041 because 0040 belongs to the open #356.

## Greenfield design

The change does not alter any decision of `docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`. Its CloudNet section
says a race server "must never inherit the previous match's directory". Option (a') keeps that, because the state lives outside
the directory. The section's assumption that a service "typically lives one cup" is the owner's; this design does not depend
on it.

## Risks

- The live rotation with `legacy_cup` opens three more worlds. Under `-Xmx512M` this must be measured in the smoke test.
  If the peak does not fit, the owner decides between a smaller rotation and a larger heap before the code PR merges.
- A slow world check at boot delays the first accepted connection. The smoke test records the boot time.
- #352 (`fix(race)`, open) edits `WaitingRoom`. The implementation rebases on it.
- The state file's directory must be writable by the service user. The boot WARN names the path, and the configuration check
  refuses a path whose parent directory does not exist.

## Open questions for the owner

- Q1. What stops the service after a cup? Voyager does not stop itself. The node's automation (the CloudNet v3 Smart module
  had an auto-stop for unused services; whether v4 has one is not verified here) or an operator might. The answer decides
  whether (c) is safe alone. (a') works either way.
- Q2. Does the live node have a directory that survives a service's stop and is outside the service directory? If yes, which
  path, and may the task environment set `VOYAGER_CUP_ROTATION_STATE` to it?
- Q3. Which cups are in the live rotation, in what order, and are all their worlds on the live node? `legacy_cup` needs
  `PalmBeach`, `DragonLobby` and `Memoria`.
- Q4. Does an aborted cup or a restarted cup (`/race restart`) advance the rotation? Recommendation: no.
- Q5. Does the check mode (validate-and-exit) treat a skipped cup as a WARNING with exit 0, or as an error with exit 1?
  Recommendation: WARNING, exit 0, while a playable cup remains.
- Q6. What is the live `minServiceCount`? The repository copy says 0, which CloudNet does not restart from. The owner's
  description implies at least 1.
- Q7. Is the live task dynamic? The repository says yes (`autoDeleteOnStop: true`, `staticServices: false` in the design). The
  live task file is not in the repository.
- Q8. Is "the next start" the next cup (the next round), as assumed here, or the next start of the service?
- Q9. Does the tutorial book get a sixth page "Next cup", or does the upcoming cup appear only on the sidebar and the hologram?
  Recommendation: the sixth page.
- Q10. Does one state file serve one service only, or should several services share one rotation? Assumed: one service.
