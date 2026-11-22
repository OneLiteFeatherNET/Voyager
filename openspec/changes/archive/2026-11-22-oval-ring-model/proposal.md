# Proposal

**Type and scope:** `feat(race)`. Ships as one code change under this title. The change is the ring model, its collision
test and its file format, so the scope names the race rule it changes. It touches the API, race, platform and the setup
server's picker and draft editor. The builder's commands for ovals are the separate change `edit-ring-shape-and-type`
(`feat(setup)`).

## Why

The owner decided on 2026-10-11 that Voyager supports oval rings as well as circles. Tilted rings already work, because the
map format accepts any unit normal. A ring is still a disc, though. The old maps were built from block outlines, and several
of their rings are clearly elliptical: palmbeach rings 0, 1, 2, 3 and 12, and memoria rings 7, 18 and 19 (the handoff's
fit). Approximating such a ring by a circle of the mean rim distance is too generous on the short axis, so a racer can pass
a ring that the course meant to be narrower.

The stored geometry of a ring is a disc today. ADR-0018 records that choice. This change amends it for the shape only:
ADR-0043 records the amendment, and ADR-0018 is not edited.

## What Changes

- A ring is a circle or an oval. An oval has a semi-major radius (the existing `radius`), a semi-minor radius
  `radiusMinor` and an in-plane unit `majorAxis`. An oval whose two radii are equal is a circle, and each shape has one
  representation.
- A point is inside a ring when it lies inside its shape. For a circle this is the current test, unchanged, so no existing
  ring changes behaviour. The rim is inclusive for both shapes.
- The collision test and the setup server's ring picker use the same containment, so what the builder points at is what
  the racer must fly through.
- Every radius of a ring, the semi-major and the semi-minor radius of an oval alike, lies in `[0.5, 32]` blocks, and a ring's
  points lie in `[0, 100]`. A ring outside these bounds is refused when it is built, so a file that states one does not load.
- The file format gains two optional ring fields, `radiusMinor` and `majorAxis`, which must be given together. A file that
  holds an oval states `"schemaVersion": 3`. A server that reads only up to version 2 refuses that file, so an oval is never
  read as a circle. A version 2 file with an oval field is refused.
- The canonical geometry hash (`LayoutVersion`) of a circle-only map does not change. A map that has an oval has a new layout
  version, and its leaderboard starts again, which is intended because its course changed.
- The setup server writes oval rings and keeps the shape of every ring it rebuilds. Removing a ring must not turn an oval
  into a circle.
- The schemas describe version 3. The version 2 schemas are kept under their own names.

## Capabilities

### New Capabilities

- `race/ring-shape`: the two ring shapes, their validity rules and bounds, the containment rule for each shape, the collision
  of a segment with an oval, and the outer bound that computer racers keep as clearance.
- `catalog/oval-ring-format`: the optional oval fields of a ring in a map or draft file, the pairing rule, the schema version
  3 rule, and the rule that the layout hash changes only for a map with an oval.

### Modified Capabilities

- `catalog/file-schema`: the map and cup schemas describe version 3 and the oval fields; version 2 schemas are kept.
- `catalog/file-versioning`: the highest schema version this server reads becomes 3, so the refusal example moves to 4.
- `setup/ring-placement`: the removal gesture uses the shape of each ring, and removal keeps the shape of the rings after it.
- `setup/map-draft-storage`: the draft JSON is written in version 3 when one of its rings is an oval, and carries the oval
  fields.

## Out of scope

- Placing, sizing and tilting an oval in the setup server. That is `edit-ring-shape-and-type` (change 3). Until it lands, an
  oval can be made by a file, and the builder's preview draws the outer disc of an oval.
- The `tools/map-converter`, its rim tolerance and its ellipse fit, and converting the old maps. Both are a separate change.
- A new in-game ring outline. The code has none today (see the design); this change adds no rendering.
- Tilt as a builder command. The oval's in-plane rotation is a builder command in change 3. This change only stores the axis.

## Impact

- `voyager/api`: `Ring` carries a `RingShape` in place of its radius; the new sealed `RingShape` with the records `Circle`
  and `Ellipse`; the `InvalidRingException` factories `radiusOutOfRange` and `pointsOutOfRange`, which replace
  `nonPositiveRadius` and `negativePoints`, and the new refusals of an oval; the containment method on `Ring`; the javadoc of
  `Ring`, which describes the hit area as the circumcircle today.
- `voyager/fitness`: one new ArchUnit rule in `DesignRuleTest` that the permitted shapes of `RingShape` are two final records.
- `voyager/race`: `RingPass.crosses`. `ComPilot` keeps its clearance on the outer bound and needs no code change.
- `voyager/setup`: `RingPicker` and `DraftEditor.withoutRing` (the rebuilt ring keeps its shape).
- `voyager/platform`: `RingAdapter` and the map and draft adapters (they pass the file's schema version), `SchemaVersion`,
  `MapDraftJsonWriter`, `MapDefinitionCanonicalWriter`, and the schemas `map.schema.json` and `cup.schema.json` with their
  version 2 copies.
- `docs/reference/map-and-cup-files.md` and ADR-0043 (in the design, moved to `docs/decisions/` by the code pull request).
- Operators: deploy the game server built from this change before any setup server writes an oval. A game server older than
  this change refuses a version 3 map; it does not play the oval as a circle.
- A file whose radius lies outside `[0.5, 32]`, or whose points lie outside `[0, 100]`, is refused from this change on. The
  shipped maps have radii from 3.6 to 7.3 and 10 points per ring, so none of them is refused.
- Persistence: no migration. The shipped maps have no oval, so their layout versions and leaderboards do not change.
