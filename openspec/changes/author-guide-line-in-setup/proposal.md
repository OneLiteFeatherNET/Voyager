# Proposal

**Type and scope:** `feat(setup)`. Ships as one code change under this title. It adds a `/map guide` command group and guide
markers in the setup world. It touches the `setup` slice only (`setup/mapsetup`, `setup/adapter`, the messages in
`platform/text`). The guide point format, `MapDefinition`'s bracket rule and the game's reading of guide points do not change.

## Why

A builder cannot author the racing line. A new map gets an empty guide line, `new GuideLine(List.of(), 2, 1.0)`
(`voyager/setup/.../mapsetup/RingDefaults.java:29`), and `DraftEditor` only copies it; no command adds, moves or removes a
guide point (`voyager/setup/.../mapsetup/DraftEditor.java`). Three shipped maps have no guide point at all (`"points": []` in
`voyager/server/src/main/resources/maps/palm_beach.json`, `memoria.json` and `dragon_lobby.json`), and `blue_and_red.json` has
16, written by hand. A guide point is a control point that pulls the line off a straight ring-to-ring segment that cuts through
terrain (`voyager/api/.../race/GuidePoint.java:9-11`), so the places where a builder needs one are the places they have seen
while flying. Writing it as JSON means choosing an `orderIndex` by hand, and a wrong index is only found at save, when
`MapDefinition` refuses a guide point that does not bracket two legal rings
(`voyager/api/.../race/MapDefinition.java:92-97`).

## What Changes

- `/map guide add` adds a guide point at the builder's eye position. Its `orderIndex` is derived from the two rings the point
  lies between, never typed by the builder.
- `/map guide list` lists the guide points of the open map, numbered from 1, with the two rings each one bends between.
- `/map guide remove <n>` removes guide point `<n>` from the list.
- `/map guide spacing <blocks>` sets the particle spacing, from 0.25 up, as `GuideLine` already requires.
- `/map guide lookahead <n>` sets the look-ahead in rings; it is shown as used only in WINDOW mode, as the game documents.
- Every guide point is drawn as a marker in the setup world while the map is open, and the markers are removed on close, as
  ring previews are.
- Every guide change goes through `MapSession.commit`: it is saved before it is shown and is one undo step.
- A guide change that would save a guide line `MapDefinition` refuses is refused before the save, with the bracket message
  named in the builder's language.

## Capabilities

### New Capabilities

- `setup/guide-authoring`: the `/map guide` subcommands, the derivation of a guide point's position on the ring order, the
  markers, the spacing and look-ahead bounds, and the refusal before save.

### Modified Capabilities

None.

## Dependencies

- Ring indices. A guide point is keyed by the ring order, so `edit-ring-order-and-position` decides what happens to guide
  points when rings are reordered or inserted. Until it lands, this change does not reorder or insert rings, so guide points
  keep their meaning. The two changes are reviewed together.
- `undo-draft-edits` makes guide changes undoable. Without it the commands still save correctly.

## Out of scope

- Bends: a guide point is a single point; a curved or spline-typed line is a later decision. Open question in `design.md`.
- Changing the game's reading of a guide point or the particle budget. The spacing limit is shown to the builder, and the
  budget decision (ADR-0030, 2.0 blocks) is not reopened here.
- Importing guide points from an existing map. The converter (`map-converter`) carries them; this change only authors.
- Guide points on a cup, or any guide point outside the map.

## Trade-offs

### Pros

- The line is authored where it is seen, from the eye, instead of as an index typed in JSON.
- `MapDefinition` already checks the bracket rule, so validation reuses the game's check instead of a second rule.
- Spacing and look-ahead are bounded by the same constructor the game uses, so the builder cannot save a value the game refuses.

### Cons

- The derived `orderIndex` depends on the ring order, so this change and `edit-ring-order-and-position` must agree on how a
  guide point follows a ring.
- Markers add entities to the setup world, which need the same cleanup as ring previews.
- Deciding which two rings a point between rings bends between is a rule builders must learn (see `design.md`).

## Impact

- `voyager/setup`: `GuidePlacement` (pure, in `mapsetup`), `DraftEditor` gains `withGuidePoint` and `withoutGuidePoint`,
  `SetupCommands` gains the `guide` subcommands, a new marker class in `adapter`.
- `voyager/platform/text`: the new messages.
- `voyager/api`: none.
- Docs: a section on the racing line in `docs/guides/how-to-build-a-map-with-setup.md`.
- Size: M to L (markers are the largest part).
