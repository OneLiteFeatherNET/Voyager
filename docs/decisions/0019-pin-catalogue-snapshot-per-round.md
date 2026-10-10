# ADR-0019: Pin the catalogue each round plays, and apply a reload at the next round start

## Status

Accepted

The project owner accepted this record on 2026-10-10. `CLAUDE.md` requires an accepted ADR before the change that
implements it is merged.

## Date

2026-10-10

## Decision makers

- Project owner (accepted 2026-10-10)
- Atlas (architect, drafted this record with the change `hot-reload-catalogs`)

## Context and problem statement

Every edit to a map or cup file needs a server restart. The catalogues are read once at boot, and the boot graph held
them for the life of the process (research 005, E1). A race server that restarts for each ring fix loses its players and
the cup in progress.

The rebuild has to let an operator apply a valid edit without a restart, keep the running server up when an edit is
broken, and never let a round mix two catalogues. A round compares medals and par times across all of its maps, so a map
swapped in mid-cup would be scored against the rotation and rings it replaced.

Research 005 (E1, open question 5) asked what a reload does to a round in progress. This record answers it.

## Decision drivers

- A round scores against one catalogue from start to finish
- A broken edit never stops the server or changes what is being raced
- No file watcher: a watcher on Docker bind mounts can look installed and never fire
- The tick (20 TPS, 50 ms budget) never waits for file I/O or validation
- The container stays compile-time (avaje-inject, ADR-0016); no bean of a catalogue type may freeze the boot catalogue

## Considered options

1. Swap the catalogue immediately, from the reload thread
2. Swap at the next map boundary inside a cup
3. Pin the catalogue at round start; a reload waits for the next round start
4. Watch `maps/` and `cups/` on disk and reload on change
5. Restart the server on any change (status quo)

## Decision outcome

Chosen option: **3, pin the catalogue at round start.**

- A **round** is one cup run, from `CupSession.start` until the cup finishes or is stopped.
- `CatalogHolder` (voyager-platform) holds the current `LoadedCatalog` and at most one pending one. `offer` only fills the
  pending slot. `promoteForNewRound` is the only place the current catalogue changes, and it is called only from
  `CupSession.start`.
- A round pins the catalogue `start` returned and reads every map, ring and cup from that pin until it ends.
- The reload is an operator command, `/race reload`, run on a virtual thread. It reads and validates the whole data
  directory with `CatalogLoader.read`, checks the worlds of the chosen cup, and opens each world that is not open. It
  applies all of the change or none of it.
- A world that is open is never reread. A world whose region files changed is reported as needing a restart.

Why not option 2: a swap between maps can change the rotation, or the rings of maps not yet entered, after the medal and
par comparison of the earlier maps has been made. Pinning removes that class of defect with no extra state.

Why not option 1: the tick reads the catalogue in `enterMap`, so an immediate swap is a race on the tick thread.

Why not option 4: see the Considered options above. The command is deterministic and testable with an injected clock. A
fingerprint poll is a possible follow-up, default off.

### Consequences

- Good: a round is internally consistent, and the swap is one reference write with no partial state visible.
- Good: a broken edit is reported in full, and the running round is untouched.
- Bad: a valid edit made during a cup waits for the next cup. The reply and `/race` say so.
- Bad: a finished cup starts a new round only when a player next joins an idle server, or with `/race start` in dev mode.
  While the server holds players and no cup runs, a pending reload waits for that join. The how-to states this.
- Bad: a reload refuses for the same problems that refuse boot, and no others. A broken cup that is not played does not
  refuse a reload; it adds one warning line to the reply, as boot logs one warning. This alignment is commit `23d1c51`
  (`fix(server): let a broken unplayed cup warn instead of refusing a reload`). Before that commit the reload checked every
  cup file and refused on any broken one, which was stricter than boot.
- Neutral: the bean graph holds the holder, not a snapshot. `MapCatalog` and `CupCatalog` ports are removed.

### Confirmation

- `CatalogHolderTest`: the current catalogue is unchanged by an offer; promotion is once per round; concurrent offers keep
  one of the offered values.
- `CupSessionRoundPinTest`: a round keeps the catalogue it started with after a later offer; the next round takes the offered
  one.
- `CatalogReloaderTest`: every problem is reported together; a world that fails to open discards the worlds this attempt
  opened; a changed open world is a warning, not a rejection.
- `VoyagerGraphTest`: no `MapCatalog`, `CupCatalog`, `CupDefinition` or `CatalogSnapshot` bean exists.
- `CatalogReloadServiceTest`: the reload runs on the given executor, never on the caller's thread.

## Pros and cons of the options

### 1. Swap immediately

- Good, because a valid edit takes effect at once
- Bad, because it is a race with the tick, which reads the catalogue in `enterMap`
- Bad, because a cup in progress can change its rotation under the medal comparison

### 2. Swap at a map boundary

- Good, because a valid edit waits at most one map
- Bad, because the rotation and the rings of unentered maps can change inside a cup
- Bad, because the cross-map comparison mixes two catalogues

### 3. Pin at round start (chosen)

- Good, because every round scores against one catalogue
- Good, because the swap point is the one the tick already has: `start`
- Bad, because the wait is up to one whole cup

### 4. File watcher

- Good, because it needs no operator action
- Bad, because on Docker Desktop bind mounts inotify events do not reliably arrive, so a watcher can look installed and
  never fire, which is worse than none
- Bad, because editors save by rename, and partial JSON and bursts need debouncing and full rescans
- Bad, because the reload delay then depends on the platform (about ten seconds on macOS)

### 5. Restart on change

- Good, because nothing new exists
- Bad, because every edit ends the running cup and disconnects players

## More information

- Design: `openspec/changes/hot-reload-catalogs/design.md`, decisions 1 to 8.
- Research: `docs/research/005-simpler-map-and-cup-setup.md` (E1, open question 5, and roadmap Q7) poses the question this record answers.
- Operator how-to: `docs/guides/reload-catalogs.md`.
- The greenfield design spec (`docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`) has no decision row that
  changes here.
