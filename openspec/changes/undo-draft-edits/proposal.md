# Proposal

**Type and scope:** `feat(setup)`. Ships as one code change under this title.

## Why

A builder who places a ring in the wrong place, or who removes the wrong ring, has no way back. The removal is permanent
(`docs/decisions/0018-pose-placement-authoring-model-for-setup.md` names the lack of undo as a consequence and leaves it to a
follow-up). A map of thirty rings can lose a ring with one sneak-click, and the builder has to place it again by hand from
memory. Each change to a draft is already saved before the builder sees it, so the previous draft is on disk until the next
edit. Undo keeps the previous drafts of the open map, so the builder can step back through the last edits.

## What Changes

- `/map undo` restores the draft before the last edit, saves it, and then replies.
- `/map redo` re-applies the edit that the last undo took back.
- A new edit clears the redo history.
- An edit is a placed ring, a removed ring, or a set spawn. Once `edit-ring-shape-and-type` and `map-name-and-authors` land,
  every ring and map field edit is an edit too, because each goes through the same commit.
- The history holds 100 snapshots of the open map (owner to confirm). The oldest snapshot is dropped first.
- The history lives in memory. It is dropped when the builder opens another map, closes the map, disconnects, or the setup
  server restarts. Opening a map is not an edit.
- An undo or redo whose save fails keeps the current draft and its history, and the builder is told that nothing changed.

## Capabilities

### New Capabilities

- `setup/draft-undo`: the undo and redo of the draft of the open map, the history limit, the lifetime of the history, and the
  behaviour when a save fails.

### Modified Capabilities

None.

## Out of scope

- Undo across maps, and undo after a restart. The history is memory only; the file on disk is the last saved state.
- Undo for cups. The cup commands of `cup-authoring` decide that (open question there).
- Undo of block edits in the setup world. `TerrainGuard` cancels them, so they never reach the draft.
- A history browser, named checkpoints, or undo of a save that another process made.

## Impact

- Vertical slices: `setup` only. `setup/mapsetup` gains a pure history value, `setup/adapter` gains the two commands and the
  session's history, and `platform/text` gains four message methods. No change to `api`, `platform` storage, `race`, or the
  game server.
- Dependency: the fix `keep-draft-metadata-on-edit` (change 6, `fix(setup)`) must land first. Without it, an undo of a spawn
  set on a map with a display name would drop the name again.
- Greenfield design: no decision changes.
- Docs: a section on undo and redo in `docs/guides/how-to-build-a-map-with-setup.md`.
- Size: M.
