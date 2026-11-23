# Design

## Context

A draft lives in `maps/` when it is game-loadable and in `drafts/` otherwise (`setup/map-draft-storage`, "A draft lives where
its completeness puts it"). A map's world lives at `<worldsPath>/<id>`. `JsonDraftStore.create` refuses any id whose draft, map
file or world folder exists (`JsonDraftStore.java:90-95`). `ImportCommand` refuses an id that has a draft
(`ImportCommand.java:100-102`), and `ImportGuard` holds one import per id at a time (`ImportGuard.java`).

`CatalogValidation` (`voyager/platform/.../catalog/CatalogValidation.java`) validates the catalogue directories given to it. It is
called from the game's `ConfigCheck` (`voyager/server/.../config/ConfigCheck.java:244`) and from `validateCatalog`. The setup
server can call the same class because `platform` is shared; the setup module keeps its own dependency rule.

`RegionCensus` (`voyager/platform/.../world/RegionCensus.java`) counts chunk statuses per region file in `Totals`, with `full`
counted for `full` and `minecraft:full`. A void template has a region file, so `holdsRegionData` is true, but its census can show
zero full chunks.

## Goals / Non-Goals

Goals:

- An id is reusable after a deliberate delete, and nothing is lost by it.
- `/map status` reports terrain and catalogue problems with the game's rules, without the tick thread waiting.
- Replace never leaves the builder with no draft when the copy fails.

Non-goals:

- Publishing, revisions, or the game's reading of a replaced world.
- Restoring from trash by a command.

## Decisions

### Delete moves, never removes

`/map delete <id> confirm` moves `drafts/<id>.json` and `<worldsPath>/<id>` to `drafts/trash/<id>-<timestamp>/`, where the timestamp
comes from the injected `Clock` in the form `yyyyMMdd-HHmmss`. The move is a `Files.move` within the setup data directory, so it is
a rename on the same file system. The command refuses, and moves nothing, when:

- the map is open by a builder (its claim, `setup/map-claims`);
- the draft is in `maps/` (game-loadable, see `publish-map-to-game`);
- a cup file in `cups/` names the id (`cup-authoring`);
- the trash folder for that timestamp already exists.

If the world folder cannot be moved, the draft is moved back, and the builder is told that nothing changed.

### Replace copies, then swaps

`/map import <folder> <id> --replace` copies the new region data into `<worldsPath>/<id>.new/`, verifies the copy with the existing
import checks, then moves the old world to `drafts/trash/<id>-<timestamp>/` and the new one into place, and finally writes the new
draft. The old draft is trashed, not overwritten. If the copy fails, the old world and draft stay, and `.new/` is removed. The
replace is refused for a draft in `maps/`, and for any id that a builder holds.

The game's open world is not affected by the swap, because the game reads a world only at its start (`publish-map-to-game`,
"A running game takes a published revision at its next start only").

### Readiness check off the tick

`/map status` computes the terrain line from `RegionCensus` and the catalogue problems from `CatalogValidation`, both off the tick
thread, through the existing off-tick executor of the setup server (the import runs its census the same way). The reply is sent
when both finish, and a timeout of 30 seconds yields "check did not finish" rather than a false ready. The check does not write
anything.

The terrain line: a world with zero full chunks is blocking. Partial chunks are reported but do not block.

### Medals

`/map medals` reads `MedalBrackets.DEFAULT` and the map's reference time and prints the four thresholds in seconds. No state changes.
The bracket multipliers are global, so the command prints them as given and does not invent per-map values.

## Dependency direction and modules

- `DraftTrash` and the replace flow live in `voyager/setup/.../adapter`, calling `platform` storage through the existing ports.
- `MapStatus`'s new lines are pure values in `setup/mapsetup`; the census and the catalogue check are called through a port
  implemented in `platform`.
- `setup` depends on `platform` only through ports defined in `setup`, so the setup module does not import `platform.catalog`
  directly from `mapsetup`. The existing `setupDomainOnlyDependsOnApiAndJdk` keeps `mapsetup` free of platform classes.

## Architecture rules

- Existing: `setupDomainOnlyDependsOnApiAndJdk`, `MessageBundleParityTest`.
- New boundary: `mapsetup` never imports `platform`; the catalogue and census checks are reached through a `setup` port. Covered by
  a new ArchUnit rule in `voyager/fitness`, `allowEmptyShould(false)`.
- New boundary: no command deletes a file under the setup data directory. `Files.delete` may remove only the temp file of an
  atomic save and the partial `.new/` copy of a replace, both created by this code. Covered by a new ArchUnit rule that the
  setup and platform storage classes call `Files.delete` only from those two methods.

## Decision record

Delete-as-move and copy-then-swap are storage decisions that a later restore or retention policy depends on. A MADR ADR records them
with the change; the number is the next free one in `docs/decisions/`.

## Open questions

- Trash retention. Options: keep forever (no disk policy), keep 30 days and prune on boot, keep the last N per id. Recommendation:
  keep forever and state the folder in the guide, until disk use is seen on the owner's machine; pruning needs a decision of its own.
- Catalogue scope for `/map status`. Options: check the whole catalogue, which may report another map's problem; check only the
  candidate map, which is faster but is not the publish gate. Recommendation: whole catalogue, labelled "catalogue", so the builder
  sees the same answer the gate will give.
- Census of a large world takes time. Recommendation: show "checking terrain" at once and the result when it finishes.
