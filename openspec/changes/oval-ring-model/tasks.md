# Tasks

Commit types and scope: `feat(race)` for the API, race, platform, setup and schema changes of this change (one code change
under the title `feat(race): allow oval rings`); `test` for the characterization test and the fitness rule, which change no
behaviour on their own; `docs` for `docs/` pages and the ADR move. Each behaviour gets a failing test (Red) before its
production code (Green). Every test follows F.I.R.S.T.: plain JUnit in api, `@TempDir` for every file, fixed points, no
wall-clock reads and no shared static state. The open questions in `design.md` are answered by the owner before section 3
starts. Task checkboxes are ticked in the follow-up pull request, never in the code pull request.

## 1. Red: the shape, the containment and the file rules

- [ ] 1.1 Red: `RingShapeTest` (api): a circle ring's `contains` gives the result of `distanceTo(center) <= radius` on a fixed grid of points in the disc's plane (`feat(race)`)
- [ ] 1.2 Red: `RingShapeTest`: an axis-aligned oval contains the points inside its ellipse and refuses a point beyond the semi-minor radius on the minor axis (`feat(race)`)
- [ ] 1.3 Red: `RingShapeTest`: a tilted oval, with its major axis at 45 degrees in the disc's plane, contains and refuses clearly inside and clearly outside points on each axis (`feat(race)`)
- [ ] 1.4 Red: `RingShapeTest`: the rim is inclusive: the end of the major axis and the end of the minor axis are inside (`feat(race)`)
- [ ] 1.5 Red: `RingShapeTest`: an oval with a semi-minor radius of zero or less, a semi-minor radius above the semi-major radius, a major axis that is not of unit length, and a major axis that is not perpendicular to the normal are each refused, with the message naming the value (`feat(race)`)
- [ ] 1.6 Red: `RingShapeTest`: `RingShape.of` with equal radii returns a circle equal to the circle of that radius (`feat(race)`)
- [ ] 1.7 Red: `RingPassTest` (race): a segment through an oval's area is a pass; a segment through the circumscribed circle outside the oval is not (`feat(race)`)
- [ ] 1.8 Red: `ComPilotTest`: the detour clearance of an oval ring is measured from its semi-major radius (`feat(race)`)
- [ ] 1.9 Red: `RingPickerTest` (setup): a look ray through an oval's area picks the oval; a ray through its circumscribed circle outside the oval does not (`feat(race)`)
- [ ] 1.10 Red: `DraftEditorTest` (setup): removing a ring keeps the shape of every ring after it, including an oval (`feat(race)`)
- [ ] 1.11 Red: `RingAdapterTest` (platform, `@TempDir`): a version 3 ring with `radiusMinor` and `majorAxis` reads as an oval; a version 2 file with `radiusMinor` is refused, and the message names the file, the ring position and the field (`feat(race)`)
- [ ] 1.12 Red: `RingAdapterTest`: `radiusMinor` without `majorAxis`, and `majorAxis` without `radiusMinor`, are each refused (`feat(race)`)
- [ ] 1.13 Red: `MapDraftJsonWriterTest`: a draft with an oval writes `schemaVersion` 3, `radiusMinor` and `majorAxis`; a circle-only draft writes `schemaVersion` 2 and the same bytes as before (`feat(race)`)
- [ ] 1.14 Red: schema test: a version 3 oval fixture validates against `map.schema.json`; a version 2 circle file validates against `map-v2.schema.json`; an oval ring without `majorAxis` fails validation and the error names `majorAxis` (`feat(race)`)
- [ ] 1.15 Red: fitness: a `DesignRuleTest` rule `ringShapeIsSealedToCircleAndEllipse`, which requires the classes that implement `RingShape` to be exactly `Circle` and `Ellipse`, both final records, with a seeded violation that the rule reports (`test`)
- [ ] 1.16 Red: confirm 1.1 to 1.14 fail for the stated reason (an oval read as a circle, a missing refusal, or a containment result that differs), and record the failure messages in the pull request (`test`)

## 2. Characterization: the circle path does not change

- [ ] 2.1 Characterization: `LayoutVersionTest` pins the layout version of every shipped map as it is on `main`, and the test passes before any production change (`test`)

## 3. Green: the shape, the containment and the readers

- [ ] 3.1 Green: `RingShape` sealed interface with the nested final records `Circle` and `Ellipse`, the validation of 1.5 in each record, and the static factory `RingShape.of` of 1.6 (`feat(race)`)
- [ ] 3.2 Green: `Ring` carries a `RingShape` in place of its radius, keeps the six-argument constructor, returns the outer radius from `radius()`, and adds `contains(Vec3)` with the perpendicularity check of design decision 4 (`feat(race)`)
- [ ] 3.3 Green: `RingPass.crosses` and `RingPicker.nearestCrossed` call `Ring.contains` (`feat(race)`)
- [ ] 3.4 Green: `DraftEditor.withoutRing` passes `ring.shape()` in place of `ring.radius()` (`feat(race)`)
- [ ] 3.5 Green: confirm that 1.8 passes without a change to `ComPilot`; if it does not, change the clearance to the outer bound and record why (`feat(race)`)
- [ ] 3.6 Green: `RingAdapter` reads the oval pair and refuses a lone field; the map and draft adapters pass the file's schema version to it (`feat(race)`)
- [ ] 3.7 Green: `SchemaVersion.CURRENT` becomes 3, and a file below version 3 that states an oval field is refused with the message of 1.11 (`feat(race)`)
- [ ] 3.8 Green: `MapDraftJsonWriter` writes the oval fields and chooses `schemaVersion` per draft; `MapDefinitionCanonicalWriter` writes the oval fields only for an oval, so that 2.1 still passes (`feat(race)`)
- [ ] 3.9 Green: the fitness rule of 1.15 passes, and every other rule in `voyager-fitness` still passes (`test`)

## 4. Schemas and documentation

- [ ] 4.1 Green: `map.schema.json` and `cup.schema.json` cap `schemaVersion` at 3 and describe `radiusMinor` and `majorAxis` with `dependentRequired`; add `map-v2.schema.json` and `cup-v2.schema.json` with their own `$id` (`feat(race)`)
- [ ] 4.2 Docs: `docs/reference/map-and-cup-files.md` describes the oval fields, the pairing rule, the version 3 rule and the refusal below version 3 (`docs`)
- [ ] 4.3 Docs: move ADR-0043 from `design.md` to `docs/decisions/0043-oval-rings.md` with the status Proposed, in the code pull request (`docs`)

## 5. Pull request

- [ ] 5.1 Open the code pull request titled `feat(race): allow oval rings`, with the body filled from `.github/pull_request_template.md`; the body states that the builder's preview draws the outer disc of an oval until `edit-ring-shape-and-type` lands, and that deployed game servers must be updated before a setup server writes an oval
