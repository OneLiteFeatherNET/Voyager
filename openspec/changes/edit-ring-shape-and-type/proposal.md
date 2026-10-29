# Proposal

**Type and scope:** `feat(setup)`. Ships as one code change under this title. It is the builder's side of `feat(race): allow
oval rings` (`oval-ring-model`), and it depends on that change, on `undo-draft-edits` and on `keep-draft-metadata-on-edit`.

## Why

The setup server places a ring with the wand and nothing else. Every ring it places is a circle of the default radius, with
ten points and the type `STANDARD`. A builder who wants a checkpoint, a boost ring, a larger ring or an oval has to remove the
ring and place another one from the same pose, and the second placement is never the same centre and direction as the first.
The model and the file format of an oval are the subject of `oval-ring-model`. This change gives the builder the commands to
use them: the type, the radius, the oval's two axes, its turn, the circle again, and the points of a ring.

## Two different tilts

This proposal uses two terms that must not be confused:

- A **tilted ring** is a ring whose normal is not parallel to the world's vertical axis. The wand already places one, because a
  ring takes the builder's look direction as its normal. No command changes a ring's normal.
- The **turn** of an oval is the rotation of its major axis inside the ring's plane, about the normal. It is set by
  `/map ring tilt <degrees>`. The command keeps the name the brief fixed, but the messages call it "turn", so a builder never
  reads the command as a change of the ring's normal.

## What Changes

- `/map ring type <STANDARD|BOOST|SLOW|CHECKPOINT>` sets the type of the ring the builder looks through.
- `/map ring radius <blocks>` sets the radius of a circle. On an oval it sets the semi-major radius and keeps the semi-minor
  radius; a value below the semi-minor radius is refused.
- `/map ring oval <major> <minor>` makes an oval, or sets both axes of an oval. Equal axes make a circle.
- `/map ring circle` drops the semi-minor radius and the major axis, and keeps the semi-major radius as the radius.
- `/map ring tilt <degrees>` turns the major axis of an oval inside its plane. It is refused for a circle.
- `/map ring points <n>` sets the points of the ring.
- Every command targets the ring the look ray crosses, nearest first, within 32 blocks, as the wand's removal does. No ring in
  reach changes nothing and says so.
- Every command saves before it replies, and each change is undoable with `/map undo` (`undo-draft-edits`).
- Bounds: radius and both oval axes between 0.5 and 32 blocks, the minor axis not greater than the major axis, points from
  0 to 100.
- A new oval's major axis is the projection of the world's up axis into the ring's plane, or world X when the ring lies flat (its
  normal is parallel to Y). An existing oval keeps its major axis when it is resized.
- The ring preview draws an oval as a block of its two axes, turned to its major axis. Circle previews are drawn as before.
- The wand's default placement does not change. A builder edits the ring after placing it.

## Capabilities

### New Capabilities

- `setup/ring-editing`: the six `/map ring` commands, the look-ray target, the bounds, the refusals and the saving of each change.

### Modified Capabilities

- `setup/ring-placement`: the requirement that rings are only appended and removed, and that no ring field changes, is replaced
  by a requirement that rings are never inserted or reordered. The removal gesture is changed by `oval-ring-model`, not here.
- `setup/ring-preview`: a ring's preview follows the ring's shape and its turn, not only its radius.

## Out of scope

- Moving a ring, or changing its normal. The centre and the normal stay as the wand placed them.
- Inserting a ring between others, or reordering rings. Only the wand appends and removes.
- Remembering the last type or size a builder used. An open question in `design.md`.
- Changing the wand's default radius, points or type (`RingDefaults`).
- The map converter, its ellipse fit, and converting the old maps. That is a separate change.
- Name, authors and reference time commands (`map-name-and-authors`), and cup commands (`cup-authoring`).
- Test flights from the setup server.

## Impact

- Vertical slices: `api` (one exception, `InvalidRingEditException`, in `api/mapsetup/exception`; the `Ring` and `RingShape`
  of `oval-ring-model` are used, not changed), and `setup` (the `/map ring` commands, the pure edits, the preview and the
  orientation, and a look-ray helper taken out of `WandListener`). `platform/text` gains the messages. No change to `race`,
  to platform storage, or to the game server.
- Greenfield design: this change does not decide the shape question. The owner's decision of 2026-10-09 that a ring is a
  disc (recorded in `docs/decisions/0018-pose-placement-authoring-model-for-setup.md`, and in the Setup section of
  `docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`) is amended for the shape by ADR-0043, which
  `oval-ring-model` proposes. This change depends on that decision being accepted. No D-number changes.
- Operators: the game servers must run the build of `oval-ring-model` before a setup server saves an oval, because only a
  draft that holds an oval is written as schema version 3 (see the design).
- Order: the circle-only commands may land before `oval-ring-model`, as a first pull request; the oval commands wait for it
  (`design.md`, open question 4).
- Docs: the ring section of `docs/guides/how-to-build-a-map-with-setup.md`.
- Size: M.
