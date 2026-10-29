# Proposal

**Type and scope:** `feat(setup)`. Ships as one code change under this title.

## Why

Two builders can open the same map today. Each gets a session with its own copy of the draft, and each save writes the whole
draft file. The builder who saves last replaces the other's rings, and nothing tells either of them. The setup server is one
process, so a claim held in memory is enough to stop this inside the server: a map has one editor at a time, and a builder
who is refused is told who holds the map. A save guard covers the cases a claim cannot see, such as a second setup server
or an edit made to the file by hand. The undo, ring-editing, naming and cup changes all assume one editor per map.

## What Changes

- A claim registry holds one holder per key. The key of a map is `map:<id>`, so a map and a cup with the same id do not
  block each other. Change 8 reuses the registry for cups with the key `cup:<id>`.
- `/map open <id>` is refused when another builder holds the map. The refusal names the holder. A builder who opens the map
  they already hold changes nothing.
- A new `/map close` releases the map the builder holds, removes its previews, and leaves the builder with no open map.
- A claim is released when the builder closes the map, opens another map (only after the new claim is granted), or
  disconnects, and by `/map unlock` (next bullet). A failed import releases the claim it took.
- `/map unlock <id>` releases the claim of a map that any builder holds, so a stuck claim does not need a restart. It is
  gated by the new node `voyager.setup.unlock` and by nothing else. The holder's open session on that map ends. The
  unlock does not open the map for the administrator. `/cup unlock <id>` in `cup-authoring` uses the same node.
- A save of a draft is refused when the stored copy no longer equals the draft the session last loaded or saved. The
  refusal says so, and the stored copy is not changed.
- Permission: `voyager.setup.use` is still checked per subcommand before any claim is taken, so a builder without it never
  holds a map. The LuckPerms group `builder` that carries `voyager.setup.use`, and the group that carries
  `voyager.setup.unlock`, are documented as operator steps.

## Capabilities

### New Capabilities

- `setup/map-claims`: the claim of a map by one builder, the refusal that names the holder, the same-builder no-op, the
  release paths, and the claims being held in memory only.

### Modified Capabilities

- `setup/map-commands`: `/map open` is refused for a map another builder holds, and the new `/map close` and
  `/map unlock` are added.
- `setup/map-draft-storage`: a save is refused when the stored copy changed since the session last loaded or saved it.

## Out of scope

- Lock files in `drafts/`. A lock file outlives a crash, so it needs a reaper; the decision is in `design.md` (ADR-0044).
- Claims across processes. A second setup server is caught by the save guard, not by a claim.
- Undo (`undo-draft-edits`), ring editing (`edit-ring-shape-and-type`), names and authors (`map-name-and-authors`), and cup
  commands (`cup-authoring`). Those changes use this registry; they are not part of it.
- Notifying the holder that another builder was refused.
- The import itself (`import-finished-world`). This change only replaces the import's temporary guard with the registry.

## Impact

- Vertical slices: `setup` (the registry, the claim lifecycle in `BuilderSessions`, the commands, the messages), `api` (one
  new method on the `DraftStore` port, one new exception in `api/mapsetup/exception`, and one new `PermissionNode` value,
  `VOYAGER_SETUP_UNLOCK`), and `platform` (the save guard in `JsonDraftStore`). No change to `race`, to the game server,
  or to the file format.
- Existing tests change where they construct `BuilderSessions`, because the registry is now a constructor argument.
- Greenfield design: no decision of `docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md` changes. The
  permission decision D13 is kept as it is.
- Docs: a section on `/map close`, `/map unlock` and sharing a map in `docs/guides/how-to-build-a-map-with-setup.md`, the
  builder group and the unlock node in `docs/guides/how-to-grant-permissions.md`, and ADR-0044 (Proposed) in
  `docs/decisions/` when the code pull request ships.
- Size: M.
