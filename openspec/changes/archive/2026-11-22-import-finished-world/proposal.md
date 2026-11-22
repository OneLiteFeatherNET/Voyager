# Proposal

**Type and scope:** `feat(setup)`. Ships as one code change under this title. It touches the setup server's command set, the
platform's world and catalogue adapters, and the API port that connects them. It does not change how a race is played or
scored.

## Why

A builder who has built a course in a finished world, with vanilla tools, has no way to bring it into the setup server.
`/map new` creates an empty void world, so the only route today is to rebuild the course block by block. The team-build
worlds are finished courses of seven to thirty-two megabytes, with sixteen to thirty-seven region files each, kept outside
the repository as read-only reference input. A builder needs to take such a world, add the rings that make it a course, and
play it.

The setup server should therefore copy a finished world's terrain into a map, read the spawn the world states, and tell the
builder what the copy holds, so that the builder checks it before placing rings.

## What Changes

- A command `/map import <world-folder> <id>` copies the overworld region data of a world from an import folder into the
  map's world folder `worlds/<id>`, creates the draft `drafts/<id>.json` with the world's spawn when the world states one,
  and replies with a report. The draft has no ring, so it is not game-loadable until a ring is placed.
- The import folder is set by `VOYAGER_IMPORT_PATH` through the configuration loader. It defaults to `imports` under the
  working directory. The server does not create it, and a missing folder is refused with the path the import expected.
- Two layouts are accepted: region files at the world root, or in `dimensions/minecraft/overworld/region/`. Any other layout
  is refused, and the message names the accepted layouts. Only the overworld is copied; nether and end data is ignored, and
  the reply says so.
- Only the region files named `r.X.Z.mca` are copied. Entities, points of interest, data packs, `level.dat` and `uid.dat`
  are not.
- The region data of one import is limited to 256 MiB and 1024 region files by default (`VOYAGER_IMPORT_MAX_BYTES` and
  `VOYAGER_IMPORT_MAX_REGIONS`). Both are checked before any file is copied.
- The spawn is read from `level.dat`: the overworld `spawn` of a modern world, or the legacy `SpawnX`, `SpawnY` and `SpawnZ`
  of an older one. It is placed at the centre of the block the world states. The reply shows the spawn and its source. When
  the block has `x` and `z` both 0, the reply adds a hint to run `/map spawn`.
- A chunk census reads each chunk's status over the copy and reports full, partial, unstated and unreadable chunks. The reply
  reports the count and the percentage of partial chunks and adds a warning. The import is never refused for partial chunks.
  A world whose data version is above the one this server reads is refused.
- The copy, the census and the draft save run off the server tick. The id is held by an in-memory import guard until the
  import ends. A failure after the copy removes the copied folder.
- `/map import` requires both `voyager.setup.use` and the new node `voyager.setup.import` (`PermissionNode.VOYAGER_SETUP_IMPORT`).
  The other `/map` subcommands keep requiring `voyager.setup.use` alone.
- Player-facing text is written in English and German.

## Capabilities

### New Capabilities

- `setup/world-import`: the `/map import` command, the import root and its refusal, the accepted layouts and folder names,
  the copy scope and size limits, the spawn read, the chunk census and the data version refusal, and the rollback and guard
  that keep a failed import from leaving a half-made map.

### Modified Capabilities

None. The draft format, the draft store and the world layout are reused unchanged.

## Out of scope

- Placing rings in an imported course and opening it. The existing `/map open` and ring commands do that.
- One editor per map. The import guard is in memory and is replaced by the claim registry of `single-builder-map-claims`
  (change 5).
- Other world formats: `.mcr` files, Sponge `.schem`, Polar and zip archives are refused. A later change adds one if the
  owner asks for it.
- Nether and end data, which are ignored, and entities and points of interest, which are not copied.
- Changing terrain after the import. The setup world cancels block edits as it does for any open map.
- Publishing the imported map to the game server. That is later work.
- Refusing an import for partial chunks. The import reports them and never refuses for them (owner decision O2).

## Impact

- `voyager/api`: the port `WorldImporter` and its records (`ImportReport`, `Census`, `WorldFolderName`, `WorldLayout`) in
  `net.elytrarace.voyager.api.mapsetup`, the import exceptions in `api/mapsetup/exception`, and the value
  `PermissionNode.VOYAGER_SETUP_IMPORT`.
- `voyager/platform`: `FolderWorldImporter` in `catalog`, a region reader and a census, and the spawn reader in `world`, and
  the import settings read through `ConfigLoader`.
- `voyager/setup`: the `import` subcommand in `SetupCommands`, the import guard and the folder-name check in `mapsetup`, and
  the wiring in `SetupBeans`. Messages in both bundles.
- `docs/guides/how-to-build-a-map-with-setup.md`, the configuration reference in `docs/reference/configuration.md`, and
  ADR-0042.
- Operators: to use the command, create the import folder, or set `VOYAGER_IMPORT_PATH`, and grant `voyager.setup.import` to
  each builder who may import. The game server is unchanged.
- Persistence: none.
- Greenfield decisions: D16 (configuration through avaje-config, ADR-0036) applies to the three new keys. No decision changes.
