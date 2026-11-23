# Proposal

**Type and scope:** `feat(setup)`. Ships as one code change under this title. It touches the `setup` slice: the commands
(`setup/adapter`), the draft store and its trash (`platform/catalog`, the adapter of `setup/map-draft-storage`), the readiness
check (`setup/mapsetup`, with the game's `CatalogValidation` in `platform`), and the messages. The game server, the draft
format and the stored maps do not change.

## Why

A builder who wants to start a map again, or to fix a name, has no supported way to do it. The root of `/map` offers only
`new`, `open`, `close`, `spawn`, `status` and `unlock` (`voyager/setup/.../adapter/SetupCommands.java:78-90`). An id that
has a draft cannot be imported again (`ImportCommand.java:100-102`), and `JsonDraftStore.create` refuses any id whose draft,
map file or world folder exists (`voyager/platform/.../catalog/JsonDraftStore.java:90-95`). The only way to reuse an id is to
delete files on the setup server by hand.

`/map status` says a map is ready when it has a spawn and one ring (`voyager/setup/.../mapsetup/MapStatus.java:34-41`). The
void world template contains a region file (`voyager/platform/src/main/resources/templates/void-world/region/r.0.0.mca`), and
`WorldFolders.holdsRegionData` accepts any `.mca` file (`voyager/platform/.../world/WorldFolders.java:61`). An empty void
course therefore reads as ready. The game's own `CatalogValidation` (`voyager/platform/.../catalog/CatalogValidation.java`)
never runs in the setup server; it runs only from the Gradle task `validateCatalog`
(`voyager/server/build.gradle.kts:291`), so a builder learns of a broken map only after the operator has run a build.

Two numbers a builder needs are invisible in the setup server: the medal thresholds follow the reference time, which is a fixed
60 seconds for a new map (`voyager/setup/.../mapsetup/RingDefaults.java:25`), and every map uses the same bracket multipliers
(`voyager/api/.../race/MedalBrackets.java:15`).

## What Changes

- `/map delete <id> confirm` removes a draft that is not published and not in a cup. The draft file and its world folder move to
  `drafts/trash/<id>-<timestamp>/`, so the builder can recover them by hand. Without `confirm` the command only explains what it
  would move.
- `/map import <folder> <id> --replace` replaces a draft that has no published revision. It copies first and swaps second, so a
  failed copy leaves the old draft in place. A draft in `maps/`, which the game loads, is never replaced by this command.
- `/map status` adds a terrain line from the existing `RegionCensus`: a world with no full chunk is a blocking problem, and the
  line says so. It also runs the game's `CatalogValidation` on the draft, off the tick thread, and lists its problems as
  blocking. This is the same check the planned publish gate uses (`publish-map-to-game`).
- `/map medals` shows the four tier thresholds for the map's reference time, read from `MedalBrackets.DEFAULT`.
- **BREAKING** for the rule that `/map import` refuses any id with a draft: the refusal now names `--replace` as the way forward
  for an incomplete draft.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `setup/map-commands`: a new requirement for `/map delete`, and the import refusal is described in `setup/world-import`.
- `setup/map-status`: the status requirement gains the terrain line and the catalogue problems; a new requirement covers
  `/map medals`.
- `setup/world-import`: the requirement "An import copies a world's region data into a new map" gains the `--replace` form.
- `setup/map-draft-storage`: a new requirement puts a deleted draft in the trash, and never deletes a stored file outright.

## Dependencies

- `publish-map-to-game` defines "published revision". `--replace` and `delete` refuse a map that has one; until that change
  lands, the only drafts that can be replaced or deleted are the ones in `drafts/`.
- `cup-authoring` defines which cups name a map. `delete` reads that list and refuses a map a cup names.

## Out of scope

- Restoring from the trash by a command. Recovery is by moving the folder back by hand in this change; a `/map restore` is a
  later decision.
- Deleting a published map or any map in `maps/`.
- Cleaning up the trash. Retention is an open question in `design.md`.
- Replacing a world the game has open: the game must restart to take a replaced world, which the operator does, as now.

## Trade-offs

### Pros

- An id can be reused, and a deleted draft is recoverable from the trash folder.
- `/map status` says what is wrong with an empty void course, using the game's own rules.
- A builder can read the medal thresholds before publishing, without running Gradle.

### Cons

- The trash folder grows without a policy until one is chosen.
- A census of a large world reads every region file; `/map status` must run it off the tick thread and may take seconds.
- The catalogue check reads the whole candidate, so a `/map status` on one draft may report a problem in another map if the
  catalogue is shared. The scope of the check is an open question.
- `--replace` depends on a notion of "published" that does not exist until `publish-map-to-game` lands.

## Impact

- `voyager/setup`: `SetupCommands` gains `delete`, `medals`, the `--replace` option of `import`; `MapStatus` gains the terrain
  and catalogue lines; a new `DraftTrash` in `adapter`.
- `voyager/platform`: `JsonDraftStore` gains `trash` and `replace`; `RegionCensus` and `CatalogValidation` are called, not changed.
- `voyager/api`: none.
- Docs: `docs/guides/how-to-build-a-map-with-setup.md` gains sections on delete, replace, status and medals.
- Size: L (four commands, the trash and the off-tick checks).
