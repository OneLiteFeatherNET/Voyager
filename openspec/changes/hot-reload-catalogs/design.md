# Design

## Context

`unify-catalog-loading` (the dependency) turns a data directory into one immutable `CatalogSnapshot` in
`voyager-platform` (`platform.catalog`), through `CatalogLoader.load(Path)`. It collects every problem into one
`InvalidCatalogException`. `ServerBeans` then holds one snapshot bean, and the `MapCatalog` and `CupCatalog` ports are
method references on it. That is still boot-only: the snapshot is built once and the graph keeps it.

`CupSession` (`voyager-server` `game/`) keeps its `CupDefinition` in a final field, builds its `XerusPhaseDriver` from
it in `start(...)`, and calls the `MapCatalog` port in `enterMap`. `VoyagerServer.main` resolves the rotation once at
boot and opens every world with `MapInstances.forWorld`, which caches one Minestom instance per world name and keeps a
Falco loader open for it.

`CupSession.tick()` runs at 20 TPS (50 ms budget). The tick guard in `VoyagerServer.scheduleTick` stops the cup if a tick
throws. So a reload that throws on the tick would stop the race, and no reload work may run on the tick.

Constraints: Java 25; avaje-inject is the container (`switch-di-to-avaje-inject`, decisions 2 to 4); DI annotations stay
in `voyager-server`; `voyager-platform` carries no DI annotation (`5b89e6b`); `CLAUDE.md` forbids reflection-based wiring.

## Goals / Non-Goals

**Goals**
- A valid edit to a map or cup applies at the next round, with no restart.
- A broken edit never changes what the running round plays, never stops the server, and is reported in full.
- The swap is atomic: no reader sees a catalogue half old and half new.
- The reload path is testable without sleeps, real time or a running server.

**Non-Goals:** the file watcher; reloads inside a round; unloading worlds; versioning and rollback; the old tree.

## Decisions

### 1. Safe point: the start of the next round, and a round is pinned

A **round** is one cup run, from `CupSession.start` until the cup finishes or is stopped. A round reads one catalogue,
the one pinned when it starts, and keeps it until it ends. The swap happens when a round starts, never inside a round.

- Why not at map boundaries: `XerusPhaseDriver` is built from the cup's rotation at `start`. A swap between maps could
  change the rotation, or the rings of maps 2 and 3 after map 1 is scored, and then the medal and par comparison across
  one cup would mix two catalogues. Pinning removes that class of defect with no extra state.
- Why not immediately: the tick reads the catalogue in `enterMap`, so an immediate swap is a race on the tick thread.
- Cost: a valid edit made during a cup waits for the next cup. The operator's reply says so, and `/race` shows the pending state.
- Open point, owner decision: a later variant may swap geometry of maps not yet entered while keeping the rotation pinned.
  Not in this change.

Research 005 (E1, open question 5) asks for this answer; this design gives it.

### 2. Snapshot, holder, one reference swap (voyager-platform)

`unify-catalog-loading` provides `CatalogSnapshot` (immutable record). This change adds two platform types beside it:

```
record LoadedCatalog(CatalogSnapshot snapshot, CupDefinition cup, Instant loadedAt) {}   // what a round pins

final class CatalogHolder {                                    // platform.catalog, no I/O, no DI annotation
    private final AtomicReference<LoadedCatalog> current;      // what rounds read
    private final AtomicReference<LoadedCatalog> pending = new AtomicReference<>();

    LoadedCatalog current()                    { return current.get(); }
    void offer(LoadedCatalog candidate)        { pending.set(candidate); }     // last valid offer wins
    Optional<LoadedCatalog> pending()          { return Optional.ofNullable(pending.get()); }
    LoadedCatalog promoteForNewRound() {                                     // called once per round start
        LoadedCatalog next = pending.getAndSet(null);
        if (next != null) { current.set(next); }
        return current.get();
    }
}
```

- `LoadedCatalog` resolves the selected cup at load time (`CupResolution`), so a round never resolves a cup itself and
  a missing cup is a rejection, not a tick exception.
- `promoteForNewRound` is called only from `CupSession.start(...)`. `getAndSet(null)` means a concurrent `offer` lands
  either in this round or in the next, and is never lost.
- The holder is platform, not race, because it holds unify's platform snapshot. A race type holding a platform type
  would reverse the dependency direction.

### 3. Trigger: an operator command now; no file watcher in this change

Decision: the trigger is `/race reload`. A file watcher is out of this change.

Why not `WatchService` now (the risk the owner asked about):
- Docker bind mounts. On Linux with a native filesystem, `inotify` delivers changes. On Docker Desktop (macOS and Windows),
  host edits reach the container through a file-sharing layer that does not reliably deliver inotify events. A watcher there
  looks installed and never fires. A silent watcher is worse than none, because an operator believes an edit is live when it
  is not.
- Network and FUSE mounts behave the same way for changes made elsewhere.
- JDK behaviour: on macOS the `WatchService` polls (default sensitivity about 10 seconds), so the reload delay depends on the platform.
- Editors save by rename-and-replace. That produces a delete and a create, and the create can fire before the content is
  complete. A watcher must debounce and tolerate partial JSON. Partial JSON is rejected safely (keep last good), but it is noise
  in the log and in the operator report.
- `OVERFLOW` events under bursts (a copy of a whole `maps/` directory) need a full rescan anyway.

Why a command is enough: the operator is at the server when a map is changed, and the command runs the same validation a
watcher would. It is deterministic and testable with a fake clock.

Follow-up (not in this change, Could): a fingerprint poll on the Minestom scheduler that stats each file (name, size,
mtime) every N seconds and reloads when the fingerprint is the same across two polls. It works on bind mounts because it uses
`stat`, not events. Its clock is injected. Enabled by config, default off.

### 4. World changes

| Change | Reloadable | Behaviour |
|---|---|---|
| Map JSON edited; its world already open | Yes | Definition applies at the next round. The world is not reread. |
| New map naming a world not open yet, with region data | Yes | World opened through `WorldOpener` and validated before the swap. |
| New map naming a world with no region data | No | Rejected; the report names the world. |
| Region files of an open world changed on disk | No (restart) | Definitions still apply; `Applied` carries a warning that the world needs a restart. |
| Map removed from the catalogue | Yes | Gone from the new snapshot. Its world stays open until restart. |
| Selected cup missing | No | Rejected. |

The reloader checks `holdsRegionData` first (cheap, no open) and opens new worlds only after every other check passes.
If opening one fails, it discards the worlds opened by this attempt and leaves the others untouched. The `WorldOpener`
port (platform) is implemented by `MapInstances`, which gains `discard(String world)`. `MapInstances.close()` closes every
world, so it cannot serve a rollback. Region fingerprints are taken at open, and an injected function compares them on reload.

### 5. Validation: reuse unify's loader, report everything

The reloader calls `CatalogLoader.load(dataDirectory)`. A problem comes back as the `InvalidCatalogException` from
`unify-catalog-loading`, and its message lists each problem. The reloader converts that to `Rejected(problems)`.

```
sealed interface ReloadOutcome permits Applied, Rejected {
    record Applied(LoadedCatalog loaded, List<String> warnings) implements ReloadOutcome {}
    record Rejected(List<String> problems) implements ReloadOutcome {}
}
```

Assumption on unify, to confirm at task 0.1: the problems must be available as a list (`InvalidCatalogException.problems()`),
not only as a message. The operator report should not parse a message. If unify does not expose the list, this change adds
the accessor to that exception in its own commit, under `refactor(platform)`, and the mismatch is recorded here.

The reloader adds the checks that unify does not own: the selected cup exists (`CupResolution`), and each new world holds
region data. Problems from all checks are gathered before the outcome is returned, so the operator gets one list.

Cup scope: `unify-catalog-loading` makes any broken cup in `cups/` a problem, so one broken unrelated cup rejects every
reload. That is safe (keep last good). The in-flight `scope-cup-validation` change (roadmap Q4) narrows that to the
selected cup, with a warning for the others. It is not a hard dependency, but it is the change that makes reloads usable
when several cups share the directory. See open questions.

### 6. Failure model: the reload never throws into the server

`CatalogReloadService.request(sender)` runs the reload and catches `RuntimeException` at its boundary:

- `Rejected`: send the problems to the sender, `WARN` once, holder unchanged.
- Any other `RuntimeException`: `ERROR` once with the cause, send "reload failed, see the log", holder unchanged.
- The tick loop never sees the reload, and the tick guard in `VoyagerServer.scheduleTick` is untouched.

`Error` (for example `OutOfMemoryError`) is not caught; it is a server-wide condition and keeps its existing behaviour.

### 7. Sequence of one reload and its swap

```
Operator        RaceCommand          CatalogReloadService      CatalogReloader        CatalogHolder          CupSession (tick)
   |  /race reload  |                        |                        |                     |                      |
   |--------------->| permission predicate   |                        |                     |                      |
   |                |  request(sender)       |                        |                     |                      |
   |                |----------------------->| [executor: virtual thread, never the tick]    |                      |
   |                |                        | reload(dataDir, clock) |                     |                      |
   |                |                        |----------------------->| CatalogLoader.load  |                      |
   |                |                        |                        | cup resolves?       |                      |
   |                |                        |                        | open new worlds     |                      |
   |                |                        |<-- Applied | Rejected -|                     |                      |
   |                |                        | Rejected: report, WARN, end                  |                      |
   |                |                        | Applied:  offer(loaded)  ----------------->| pending = L'         |
   |<-- "pending for the next round" -------- |                        |                     |                      |
   |                |                        |                        |                     | round N: holds pin L |
   |                |                        |                        |                     |                      |
   |                |                        |                        |   promoteForNewRound()  <-- start() of N+1 |
   |                |                        |                        |                     | current = L'        |
   |                |                        |                        |                     | returns L' --------->| pin L' for round N+1
```

Read in words: the command returns at once. Loading, validation and world checks run on a virtual thread. The only shared
write is `offer`, and the only promotion is one `getAndSet` at round start. A round pins the `LoadedCatalog` it received, and
`enterMap` reads only that pin.

### 8. Wiring with avaje-inject, without reflection

What changes in the graph, relative to `unify-catalog-loading`:

- The `CatalogSnapshot` bean becomes the boot value of the holder. Nothing injects it after this change.
- The `MapCatalog` and `CupCatalog` port beans (method references on the boot snapshot) and the `cup` bean are removed.
  Each would hold the boot catalogue and go stale on reload. `CupSession` reads the pinned `LoadedCatalog` instead.
- New `@Bean CatalogHolder catalogHolder(@External ServerSettings settings)` in `ServerBeans`, body
  `new CatalogHolder(reloader.loadInitial(settings.dataPath(), settings.cupName()))` (`reloader` is the injected
  `CatalogReloader` bean; `loadInitial` throws `IllegalStateException` naming every problem). It throws at boot when the
  catalogue is refused, so the boot refusal and `VoyagerStartupTest` stay as they are.
- `CatalogReloader` (platform, with `WorldOpener`) and `CatalogReloadService` are beans in `ServerBeans`. The service takes the
  executor as a constructor parameter, so tests pass a direct executor.
- `CupSession` takes `CatalogHolder` in place of `CupDefinition` and `MapCatalog`. `RaceBeans` changes accordingly.

Why a holder and not an injected snapshot: avaje resolves a bean once and holds it. A bean of a catalogue type freezes the
boot catalogue, which is the defect this change fixes. The holder is the one singleton, its content changes, and every
consumer reads a value through `current()` or through its round pin.

Why no reflection: avaje generates construction code at compile time. `CatalogHolder` is an ordinary class with one
constructor, and the generated code calls it directly. `LoadedCatalog` and `ReloadOutcome` are never looked up in the container.
A missing `CatalogReloader` is a compile error in the factory, as the switch-di spike (1.1) showed.

Holder rules: it implements no catalogue port (avoids the ambiguous-port problem recorded in switch-di decision 8); it has one
constructor; it has no DI annotation.

### 9. Module placement and dependency direction

```
voyager-server      CatalogReloadService, RaceCommand ("reload"), ServerBeans, RaceBeans, CupSession, VoyagerServer
   | depends on
voyager-platform    CatalogSnapshot, CatalogLoader (unify), CatalogHolder, LoadedCatalog, CatalogReloader,
   |                ReloadOutcome, WorldOpener (implemented by MapInstances)
   | depends on
voyager-race        (unchanged; CupDefinition, MapCatalog, MapDefinition from voyager-api)
   | depends on
voyager-api         CupDefinition, MapDefinition, MapCatalog, CupCatalog (unchanged)
```

| New type | Module | Package |
|---|---|---|
| `LoadedCatalog` | voyager-platform | `net.elytrarace.voyager.platform.catalog` |
| `CatalogHolder` | voyager-platform | `net.elytrarace.voyager.platform.catalog` |
| `ReloadOutcome` | voyager-platform | `net.elytrarace.voyager.platform.catalog` |
| `CatalogReloader`, `WorldOpener` | voyager-platform | `net.elytrarace.voyager.platform.catalog` |
| `CatalogReloadService`, `reload` literal | voyager-server | `net.elytrarace.voyager.server.game` / `command` |

No new fitness rule. `ApiPurityTest` and `FitnessCoverageTest` already cover the DI boundary, and the platform classes carry
no DI annotation. The reloader is a platform adapter, so it may use `java.nio` through `CatalogLoader`.

### 10. Threading and timing

- Loading, parsing, validation and world checks run on a virtual thread from the injected executor, never on the tick.
- World opening: `MapInstances.forWorld` creates a Minestom instance and registers it. Whether instance registration is safe from
  a non-tick thread is not verified. Spike 1.2 decides. If it is not safe, new worlds open on the tick at round start (one stall
  of the load time), or new worlds require a restart. Owner decision either way.
- `loadedAt` comes from an injected `Clock`. Tests use `Clock.fixed`.

## Risks / Trade-offs

- **Minestom instance registration off the tick thread** → Spike 1.2. Fallback in decision 10 needs an owner decision.
- **A rejected reload leaves a world loaded** → `MapInstances.discard(world)` for worlds opened by that attempt only, with a test
  that a rejected reload leaves `MapInstances` as it was (task 3.5).
- **Unify does not expose the problem list** → Assumption in decision 5; checked at task 0.1 before any code.
- **A finished cup may not start a new round by itself** → The apply point is "a round starts". If production only starts a cup
  on `/race start` or on a restart, a pending reload waits for that. Spike 1.1 records the production path.
- **Operator confusion about "pending"** → Reply and `/race` status both say "applies at the next round"; the how-to repeats it.
- **Permission source unknown** → Spike 1.3 checks the Minestom permission API; until the owner names the source, only the console
  can reload (the safe default).
- **One broken cup blocks every reload** → Until `scope-cup-validation` lands (decision 5). Safe, but reported as an open question.
- **Test flakiness from async execution** → The executor is injected. Tests pass a direct executor, so the reload finishes before
  the assertion. No sleep, no real time.

## Migration Plan

One PR, squash-merged, stacked on `unify-catalog-loading` if that is not yet on `main`. Branch commits by type, one type each:
`refactor(server)` for the holder seam and the removal of the port and `cup` beans (tests first); `feat(server)` for the reloader,
round pin, command and graph; `test(...)` where a test lands alone; `docs(...)` for the how-to, the ADR and research 005 status. The
squash commit on `main` is `feat(server): pick up changed maps and cups between rounds without a restart`.

Rollback: revert the squash commit. Catalogues then load at boot only, as before. No data migration and no change to files on disk.

## Open Questions

1. **Owner:** may a valid edit wait for the next cup (decision 1), or must geometry-only changes apply at the next map boundary too?
   Pinned rounds only is recommended (no mixed-catalogue comparison within a cup). The alternative is geometry-only map-boundary swaps.
2. **Owner:** `scope-cup-validation` before this change, or accept that a broken unrelated cup blocks reloads until it lands?
3. **Owner:** which permission source grants `voyager.race.reload` on the live server?
4. **Spike 1.1:** does a finished cup start again by itself in production, or only by `/race start` or a restart? The answer sets the
   "pending" wording.
5. **Spike 1.2:** instance registration from a non-tick thread. If unsafe, choose between a one-time tick stall for new worlds and "new
   worlds need a restart".
6. **Owner:** is the pinned-round answer to research 005 open question 5 the one the owner expects?
