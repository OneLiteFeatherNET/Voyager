# Proposal

**Type and scope:** `fix(setup)`. Ships as one code change under this title.

## Why

A builder edits a map in the setup server by placing a ring, removing a ring or setting the spawn. Each edit is saved at
once. Since `add-catalogue-keys-and-authors`, a map can carry a display name and authors, and a save of a map the setup
server loaded keeps both. Every edit, however, rebuilds the draft in `DraftEditor`, and that rebuild calls the constructor
which states no display name and no authors (`MapDraft.java:43-46`, called from `DraftEditor.java:76-79`).

So the first ring placed, removed or spawn set on a named map drops its name and its authors from the file, with no error
and no message. The builder sees the map as before. The next save writes the map without them, and a player who joins
sees the map id where the name should be. That is the same symptom as the playtest finding in
`add-catalogue-keys-and-authors`, reached through a different path.

## What Changes

- Every draft edit made in the setup server keeps the display name and the authors of the draft it edits: a ring
  appended, a ring removed (with the renumbering of the rings after it) and a spawn set.
- No other field changes with an edit. The file format, the commands and the messages do not change.
- Each edit gets a regression test, and one round trip through the draft store shows that the saved file keeps both fields.

## Capabilities

### New Capabilities

- `setup/draft-editing`: the rule that a draft edit changes the fields the edit names and keeps the others, applied to the
  display name and the authors. It states for every edit what `setup/map-draft-storage` (from
  `add-catalogue-keys-and-authors`) states for a save. The main specs do not hold that requirement yet, so this change
  states the edit rule on its own.

### Modified Capabilities

None.

## Out of scope

- Setting or editing a map's name and authors. Those commands are `map-name-and-authors` (change 7).
- Undo and redo. The history of `undo-draft-edits` (change 4) stores whole drafts, so it inherits this fix.
- Replacing the rebuild helpers with methods on `MapDraft` (for example `withRings`). That is an API change and is
  listed as an open question in the design.
- The `skeleton` of a new map. A new map has no name and no authors, which is correct.
- The greenfield design decisions D1 to D16 are not changed.

## Impact

- `voyager/setup`: `DraftEditor` (the private `with` helper) and `DraftEditorTest`, plus one round-trip test through the
  draft store. The fix stays in the setup slice's pure `mapsetup` package.
- `voyager/api`, `voyager/platform`, `voyager/race` and `voyager/server`: no change.
- No operator action. A map already saved without its name keeps that state until a builder sets the name again, which
  change 7 makes possible.
- No page in `docs/` describes the lost fields, so no documentation change is needed.
