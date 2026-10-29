# Design

## Context

A map is a draft under `<dataPath>/drafts/<id>.json` (or `maps/<id>.json` once game-loadable) and a world folder at
`<worldsPath>/<id>`. Today the only way to create the world is `DraftStore.create`, which copies the void template
(`voyager/platform/.../catalog/JsonDraftStore.java:89-107`, and `VoidWorldTemplate.copyTo` at
`world/VoidWorldTemplate.java:39`). An import needs a different source, so `create` cannot serve it. The port is
`api/mapsetup/DraftStore.java`, with `create`, `load` and `save` (`:19`, `:28`, `:37`).

The world folder layout is read by `WorldFolders`: the dimension path `dimensions/minecraft/overworld/region`
(`world/WorldFolders.java:21`), with the legacy top-level `region/` accepted too (`:44-53`). `MapInstances` reads a world
through Falco, one region at a time (`readEveryChunk` at `world/MapInstances.java:165`). Its report deliberately leaves out
Falco's count of skipped chunks, because that count folds in partially generated chunks (the javadoc of `report`), so the
existing `WorldHealth` cannot see a partial chunk. A census of statuses must therefore read the region records itself.

The setup server creates only its data and worlds directories (`SetupServer.java:189-198`). An import folder is not created,
and this change keeps that rule.

Measurements from the team-build worlds, read-only and external to the repository, are inputs and not settled facts. The
recorded `level.dat` of two worlds has data version 4438 and a spawn only in the legacy `Data.SpawnX/Y/Z` keys, which read
0, 65, 0. Every world mixes namespaced and bare chunk statuses, and about a third of the chunks of one world are partial.
The spike of task 4.1 verifies these.

## Goals / Non-Goals

Goals:

- A builder can make a map from a finished world in one command, with the terrain copied and the spawn read.
- A failed import leaves neither a world folder nor a draft behind.
- The import never reads the source folder again after it has copied it, so the source may change without affecting a map.
- The import does not block the server tick.

Non-Goals:

- Other world formats, the nether and end, entities and points of interest.
- Choosing the spawn for the builder. The import reports the spawn it reads.
- Opening the map. The builder runs `/map open` after the reply.

## Decisions

1. **The import copies region files into `worlds/<id>` (ADR-0042).** The copy is a snapshot. The game server reads only
   `worlds/<id>`, as it does for every map. *Rejected:* a symbolic link from `worlds/<id>` to the source, because the game
   server cannot rely on a link outside its worlds directory. *Rejected:* reading the import folder at play time, because a
   change to the source would then change a race that is running.

2. **A sibling port, `WorldImporter`, in `api/mapsetup`.** `DraftStore` keeps its meaning, and the import is its own job:
   it takes a source folder, copies it, surveys it, and returns a report. *Rejected:* an `importFrom` method on `DraftStore`,
   which would widen the port every `/map` command uses, and mix a second source into `create`. The implementation is
   `FolderWorldImporter` in `platform/catalog`, beside `JsonDraftStore`.

3. **Three steps with a rename.** The import copies into a hidden folder `worlds/.<id>.importing`, surveys the copy, and
   renames it to `worlds/<id>` only when the survey does not refuse. The draft is saved last, and if the save fails the
   renamed folder is removed (`WorldImporter.discard`). A hidden name cannot clash with a map: an id starts with a letter or
   digit (`MapId`). A rename on one file system is atomic, so a crash leaves a hidden folder, never a half-named world.

4. **The census reads the copy, with the server's own region reader.** The census reads each region's header and each chunk
   record, decompresses it, and reads its top-level `Status` and `DataVersion` with Adventure's NBT reader, which is on the
   classpath through Minestom (`setup/adapter/Wand.java:3`). It reads one region at a time, so memory is bounded. *Rejected:*
   Falco, through `MapInstances`, because its report does not carry the partial-chunk status that the census needs (see
   Context). *Rejected:* reading the source instead of the copy, because the census would then describe a folder that the
   copy may not match.

5. **The spawn is taken from the world, shown to the builder, and hinted when it looks default.** The reader takes the
   overworld `Data.spawn` of a modern world, then the legacy `Data.SpawnX/Y/Z`. It places the spawn at the block centre,
   `(x + 0.5, y, z + 0.5)`. The reply always shows the block the world states and the source of it, modern or legacy keys.
   When that block has `x` and `z` both 0, the reply adds a hint to run `/map spawn`, because the legacy keys of the
   team-build worlds read 0, 65, 0, which may not be the builder's spawn. A world with no spawn leaves the spawn unset, and the
   reply says so. *Rejected:* refusing a world with no spawn, because the copy is still useful and the draft stays in `drafts/`
   until a ring is placed. *Rejected:* dropping a default-looking spawn, because the builder sees the value and can correct it
   with `/map spawn`.

6. **A world newer than the server is refused; an older world is accepted, pending the spike.** The highest data version this
   server reads is 4903, Minestom 26.2's data version (`docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`,
   Setup section). A `level.dat` or chunk above 4903 is refused, and the copy is removed. An older world (4438 is recorded) is
   accepted. Whether Falco 3.0.1 loads it is open question 1, which the spike of task 4.1 answers; it is a spike, not an
   owner question.

7. **The census reports the partial share and never refuses an import for partial chunks.** This is the owner's decision
   (O2). The reply reports the count of partial chunks and their percentage, the share of all chunk records counted with
   unreadable ones included, and adds a warning. The import is not refused for partial chunks, and no threshold exists.
   *Rejected:* a partial-chunk threshold that refuses an import, because the owner chose to report only.

8. **Off the tick, with replies on the main thread.** The copy, the census and the save run on a thread of the setup slice,
   not on the server tick. The reply is sent on the main thread through the Minestom scheduler. The import waits on no
   future on the tick: `TickBoundaryTest.noBlockingOnFuturesOnTheTick` covers `race` and `platform`, and this design keeps
   the setup slice's job free of `get` and `join` on the tick as well.

9. **An in-memory import guard per id.** `ImportGuard` (pure, in `setup/mapsetup`) holds the ids that are being imported, so a
   second import of the same id is refused. Change 5 (`single-builder-map-claims`) replaces it with the claim registry, and
   this design does not keep a second registry.

10. **Configuration through the loader.** `VOYAGER_IMPORT_PATH`, `VOYAGER_IMPORT_MAX_BYTES` and `VOYAGER_IMPORT_MAX_REGIONS`
    are read once, through `ConfigLoader.get` (`platform/config/ConfigLoader.java:77`), into an `ImportSettings` record that
    the composition root passes to `FolderWorldImporter`. The defaults are `imports`, 268435456 bytes (256 MiB) and 1024
    region files, as the owner set them. The new code reads no system property. The existing
    `SetupSettings.fromEnvironment` reads `System::getProperty` (`setup/config/SetupSettings.java:54`), and this change does
    not copy that pattern.

11. **Folder names and links.** A folder name matches `[A-Za-z0-9_.-]{1,64}`, is not `.` or `..`, and resolves under the real
    import root. A region file that is a symbolic link is refused before anything is copied, because the copy would otherwise
    follow a link out of the root.

12. **Two layouts at once are refused.** When both `region/` and the dimension layout hold region files, the import is refused
    and names both. This follows the principle of `map-draft-storage`: two copies of one thing are refused, never resolved
    silently.

13. **The import does not open the map.** Opening needs the main thread and an instance. The reply names the next command,
    `/map open <id>`.

14. **`/map import` has its own permission node.** It requires both `voyager.setup.use` and `voyager.setup.import`, the
    value of the new `PermissionNode.VOYAGER_SETUP_IMPORT`. Every other `/map` subcommand requires `voyager.setup.use` alone.
    A builder who holds `voyager.setup.use` but not the import node can place rings and cannot import. *Rejected:* gating the
    import by `voyager.setup.use` alone, because an operator could then not withhold the import from a builder who places
    rings.

## ADR-0042 (proposed): An imported world is copied into the map's world folder

Status: Proposed, on 2026-10-29, with the change `import-finished-world`. The record moves to Accepted when the owner approves
it, as ADRs require.

Number: 0042 is the next free number after ADR-0041, and it precedes ADR-0043 of `oval-ring-model`.

### Context

A builder's finished world sits in a folder outside the game and the setup server. The setup server must make it a map, and the
game server must play it. The game server reads one place for a map's world, `<worldsPath>/<id>`, and the setup server's worlds
folder is the only place it writes. The question is whether the map's world is a copy of the source, a link to it, or a read of
the source at the time of play.

### Decision drivers

- A race in progress must not change because a file in the import folder changed.
- The game server must not depend on a path outside its worlds folder.
- A failed import must leave nothing that the game could load.

### Decision

Copy the region files into `worlds/<id>`, so the map's world is a copy of the source from the moment of the import. The
copy is the map's world, as the void template already is, so the game server reads one kind of world. Its cost is disk
space: a team-build world is 7 to 32 MB, and the import limits of this change cap one import.

*Rejected:* a symbolic link from `worlds/<id>` to the source folder, because the game server would follow a link out of its
worlds folder and would see every later change to the source.

*Rejected:* reading the import folder when the map is played, because the source would then be a runtime dependency of the
game, and a change or a removal during a race would reach the race.

### Consequences

- Good: the game server is unchanged, and it reads the same folder for every map.
- Good: a change to the source during or after the import does not reach a map.
- Bad: the disk holds one copy per import. The size limit of 256 MiB bounds one import, not the total.
- Neutral: a builder who wants a newer version of the source imports again under a new id, or removes the old map first, as the
  existing `/map new` rules require.

### Confirmation

The rollback tests of 1.13 and the copy tests of 1.7 show that the world folder is a copy and that a failed import leaves no folder.
The integration test of 4.2 reads the copy, not the source, through `MapInstances.checkWorld`.

## Dependency direction

```
setup.adapter (SetupCommands, the reply)  ──►  setup.mapsetup (ImportGuard, folder-name rule)
        │                                             │
        ▼                                             ▼
   api.mapsetup (WorldImporter, ImportReport, Census, WorldLayout, WorldFolderName, MapId)
        ▲
        │ implements
platform.catalog (FolderWorldImporter) ──► platform.world (RegionReader, RegionCensus, SpawnReader, WorldFolders)
        ▲
        │ reads through
platform.config (ConfigLoader)
```

The port and every record of it live in `voyager-api`, with no I/O, no `Path` and no Minestom type (`ApiPurityTest`). The
platform implements the port and reads the disk. `setup.mapsetup` depends on the API and the JDK only, as
`ApiPurityTest.setupDomainOnlyDependsOnApiAndJdk` requires, so `ImportGuard` and the folder-name rule hold no platform type.
`SetupCommands` depends on the port, not on the platform adapter: `SetupBeans` (the composition root) wires the platform
implementation. Each boundary is covered by an existing rule:

- Configuration read only through `ConfigLoader`: `ConfigurationBoundaryTest.environmentAccessIsConfinedToTheEdge`.
- No blocking future waits on the tick: `TickBoundaryTest.noBlockingOnFuturesOnTheTick`.
- The API carries records, interfaces and exceptions: `DesignRuleTest.apiTypesAreRecordsInterfacesOrEnums`. The new import
  exceptions are in `api/mapsetup/exception` with a `package-info.java`, and `DesignRuleTest` checks the exception rules.
- The permission node: `PermissionNode` is a pure enum in `voyager-api`, and `PermissionNodeTest` covers the new value (task 1.18).
- No new fitness rule is needed. The new records and interfaces are covered by these rules as they stand.

## Testing

All tests follow F.I.R.S.T. and "Red/Green TDD".

- Unit tests build synthetic region files and `level.dat` files in `@TempDir`. A small test writer produces the region header,
  the chunk records and a gzip NBT `level.dat`. No test reads the team-build worlds.
- Size limits are injected through `ImportSettings`, with small values, so no test writes 256 MiB.
- The census tests use a fixed set of chunks: a namespaced full, a bare `full`, a partial, a chunk without status, and an
  unreadable record.
- The rollback tests make the draft save fail through a test `DraftStore` that throws, and check that the world folder is
  gone afterwards.
- No test sleeps, reads the system time, uses a network, or writes outside `@TempDir`. The background thread is driven by a
  test executor that runs the task on the calling thread, so the result does not depend on scheduling.
- One integration test, tagged `integration` as in `voyager/platform/build.gradle.kts:108-118`, imports a real team-build world
  when it is present and skips with a message when it is not. It is not part of CI. It runs `MapInstances.checkWorld` on the
  copy, which answers open question 1 and the chunk claims of the Context.
- Messages in both bundles are checked by the existing `MessageBundleParityTest`.

## Open questions

1. **Older worlds in Falco 3.0.1.** A world at data version 4438 is accepted by the rule of decision 6. This is answered by the
   spike of task 4.1, not by the owner: the spike runs `MapInstances.checkWorld` on an imported copy of a team-build world. If
   Falco cannot load it, the importer refuses that world and names its version, as decision 6 describes. The spike result is
   recorded in the pull request.
