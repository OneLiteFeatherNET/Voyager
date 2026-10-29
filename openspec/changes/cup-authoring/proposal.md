# Proposal

**Type and scope:** `feat(setup)`. Ships as one code change under this title. It adds a `/cup` command root to the setup server and writes cup files into the setup data directory. It does not change the game server, how a cup is played, or the statistics.

## Why

A builder can now make a map in the setup server (`/map new`, `/map spawn`, rings, and after `map-name-and-authors` a name and authors), but a cup, the rotation of maps a race plays, can only be written by hand in `cups/<id>.json`. The hand-written file is where a wrong map name or an unplayable rotation enters the game: the game refuses a cup that names an unknown map, but only at boot, and only after the operator has copied the file to the game server.

The setup server should be able to create a cup, name it, choose its mode and order its maps, and refuse, at save time, any map the game would not load. The file it writes is the one the game server reads, so there is no conversion step.

## What Changes

- `/cup new <id> <RACE|PRACTICE> <map>` creates a cup with its first map and opens it for the builder, as `/map new` opens the map it creates. A cup needs at least one map, because `CupDefinition` refuses an empty rotation, so `new` names it.
- `/cup open <id>` claims an existing cup for the builder and prints its definition: its key, mode and display name, and each map with its 1-based position and whether the game can load it. It is refused, and the holder is named, when another builder holds the cup. `/cup open` replaces the former `/cup info`.
- `/cup name <id> <text|clear>`, `/cup mode <id> <RACE|PRACTICE>`, `/cup map add <id> <map> [position]`, `/cup map remove <id> <position>` and `/cup map move <id> <from> <to>` edit a cup. Only the builder who holds the cup's claim may run them. Positions are counted from 1, in the order `/cup open` prints.
- `/cup unlock <id>` releases the claim on a cup, whichever builder holds it. It needs the node `voyager.setup.unlock`. There is no `/cup close`: a claim is released when the builder opens another cup, when the builder disconnects, or by an unlock.
- A map named in a cup must be game-loadable: its file is in the setup data directory's `maps/`, which is the directory the game reads. A draft map is refused with a message that says what the builder must do first.
- A cup is written as version 2 with its `key` `elytrarace:<id>`, to `<dataPath>/cups/<id>.json`, through an atomic write. The display name and the `notes` array of a cup that is edited are kept. A cup carries no authors: the writer writes none, and a save of a file that states `authors` is refused.
- Each command saves before it replies, as the `/map` commands do. A failed save keeps the previous file.
- The player-facing messages are in both setup bundles.

## Capabilities

### New Capabilities

- `setup/cup-commands`: the `/cup` subcommands, their argument rules, the claim a builder holds on a cup, the unlock, the refusals, and the rule that a cup names only game-loadable maps.
- `setup/cup-storage`: the file the setup server writes for a cup, its version, its key, its folder, the notes it keeps, the refusal of authors, the atomic save, and the check against the map catalogue.

### Modified Capabilities

None. The map commands and the map draft format are unchanged. The game server's cup reader is unchanged; the file this change writes is one it already reads.

## Out of scope

- Undo and redo of cup edits. Cups are not covered by undo; `undo-draft-edits` covers map edits only. A mistyped position is corrected by another command.
- Authors on a cup. A cup carries no authors, so the setup server neither adds nor removes them. `CupDefinition` keeps the field for the format; this change does not use it.
- Publishing: copying a written cup to the game server. The operator copies it, as for maps (ADR-0045, Proposed). A publish command is later work.
- Changing the rule of the catalogue's dangling-entry check. This change calls it; it does not change it.
- Statistics, scoring and race flow.

## Impact

- Vertical slices: `setup` (the `/cup` command logic as a pure port and service in `setup.cupsetup`, the Minestom wrapper in `setup.inject`, the messages), `api` (a `CupStore` port, a `CupId` value, and exceptions, under `net.elytrarace.voyager.api.cupsetup`), `platform` (`JsonCupStore` and `CupDefinitionJsonWriter` beside the draft store and its writer, and an extracted atomic-write helper shared by both stores, its own commit). The `race` slice is not touched.
- `voyager/setup`: `SetupServer` registers the `/cup` wrapper next to `/map`; `SetupMessages` and both bundles get the new keys.
- `voyager/server`: none. The game reads the written files through its existing cup and map adapters.
- Docs: `docs/guides/how-to-build-a-map-with-setup.md` gets a section "Build a cup"; `docs/reference/map-and-cup-files.md` gets the cup part of the setup commands.
- Depends on `map-name-and-authors` (change 7), for the name rules, and on `single-builder-map-claims` (change 5), for the claim registry (`EditClaims`, key `cup:<id>`) and for the permission node `voyager.setup.unlock` (`PermissionNode.VOYAGER_SETUP_UNLOCK`), which that change adds.
- Operators: a builder with `voyager.setup.use` can run `/cup`. An administrator with `voyager.setup.unlock` can run `/cup unlock`. No new permission node is added by this change.
