# Proposal

**Type and scope:** `feat(setup)`. Ships as one code change under this title. It touches the `setup` slice only: the draft
editor (`setup/mapsetup`), the commands and the wand (`setup/adapter`), and the messages in `platform/text`. The stored draft
format and the game server do not change.

## Why

A builder who places a ring a metre too far east has two ways out today, and both cost the course. The wand appends only
(`voyager/setup/.../mapsetup/DraftEditor.java:37-39` accepts an index only when it equals the number of rings), and
`/map ring` removes nothing and moves nothing. The only fix is to remove the ring and every ring after it, then place them
again from memory. A new ring cannot be placed between two others at all. The ring pose is taken from the builder's eye
(`voyager/setup/.../mapsetup/RingFromPose.java:27-34`), so a builder who stood one step off cannot correct the centre
without standing there again.

The guide points make this more than a list edit. A guide point is keyed by its `orderIndex` on an axis shared with the
rings, where ring `i` sits at slot `i * 100` (`voyager/api/.../race/GuidePoint.java:15-22`), not by a ring index. The
`blue_and_red.json` course carries 16 guide points (`voyager/server/src/main/resources/maps/blue_and_red.json`, field
`guideLine.points`), and an insert or a reorder moves rings on that axis, so the guide points must follow the rings they
bend between in the same save.

Change 5 of the setup plan, `edit-ring-shape-and-type`, names moving, inserting and reordering as out of scope
(`openspec/changes/edit-ring-shape-and-type/proposal.md:52, 58`). This change is the follow-up it leaves open.

## What Changes

- `/map ring move <index> here` sets the centre of ring `<index>` to the builder's eye position. Its normal is unchanged.
- `/map ring move <index> <dx> <dy> <dz>` moves the centre of ring `<index>` by the given offset in blocks.
- `/map ring insert <index>` places a new ring from the builder's pose at position `<index>`, where `<index>` is between 0
  and the number of rings. The rings after it are renumbered, and it is the one place that takes a pose and an index
  together.
- `/map ring reorder <from> <to>` moves the ring at `<from>` to position `<to>`; the rings in between shift by one.
- Each command goes through `MapSession.commit`: it is saved before the builder sees it, shows the new previews, and is
  one undo step of `undo-draft-edits`.
- Every guide point stays on the ring it bends between. The guide points are remapped in the same draft as the rings, so
  the saved draft never has a guide point on a slot that now holds another ring.
- **BREAKING** for the rule in `setup/ring-placement`: "Rings are never inserted or reordered" is replaced by a rule that
  rings are inserted or reordered only by the named commands above.

## Capabilities

### New Capabilities

None. The commands extend `setup/ring-editing` from `edit-ring-shape-and-type`.

### Modified Capabilities

- `setup/ring-placement`: the requirement that rings are never inserted or reordered is replaced, as described above.
- `setup/ring-editing`: four requirements are added, one for each command; the bounds and refusals are those of
  `edit-ring-shape-and-type`.
- `setup/draft-editing`: a requirement that a guide point stays on the ring it bends between when a ring moves, is
  inserted or is reordered.

## Dependencies

- `edit-ring-shape-and-type` must be archived first. This change modifies its replacement requirement and extends its
  `setup/ring-editing` capability. Until then the delta files are validated, not archived.
- `undo-draft-edits` must be in place for "one undo step" to hold. Without it, the commands still save correctly.

## Out of scope

- Changing a ring's normal (the aim) with a command. `here` keeps the normal; aiming is a separate question in `design.md`.
- Moving a ring with the look ray or by dragging. Only the typed offset and `here` are supported.
- Moving or re-aiming guide points by hand. Guide points are authored by `author-guide-line-in-setup`.
- Any change to the stored format or to the game's reading of guide points.

## Trade-offs

### Pros

- A ring can be corrected in place, and the rings after it keep their places.
- A ring can be inserted where the course needs it, without a rebuild from the tail.
- Each move is one undo step, and the guide line stays where it was drawn.

### Cons

- Four new commands on top of six, and the index syntax is one more thing to learn.
- The guide remapping is new logic with its own tests, and it is the risky part of the change.
- Previews and the undo history must agree after a reorder; both are tested through the same commit path, so a missed
  path is one code review away.

## Impact

- `voyager/setup`: `DraftEditor` gains `moved`, `inserted` and `reordered` operations; `GuideRemap` is a pure class in
  `mapsetup`; `SetupCommands` gains the `ring` subcommands; `WandListener` is unchanged.
- `voyager/platform/text`: the new messages.
- `voyager/api`: none. The `MapDefinition` checks already reject a broken guide line at save.
- Docs: `docs/guides/how-to-build-a-map-with-setup.md` gains a section on moving, inserting and reordering rings. An ADR
  (MADR) records the index addressing and the guide remapping rule (the next free number in `docs/decisions/`).
- Size: M.
