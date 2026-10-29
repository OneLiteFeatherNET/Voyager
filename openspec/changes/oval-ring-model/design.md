# Design

## Context

A ring is a disc. `Ring(int index, Vec3 center, Vec3 normal, double radius, int points, RingType type)`
(`voyager/api/.../api/race/Ring.java:14`) validates `radius > 0` at `:22-24` and the unit normal at `:28-30`. The radius is
read and used in these places, found by a grep of the rebuild modules:

| Site | Use | Effect of an oval |
|---|---|---|
| `race/collision/RingPass.java:44` | crossing point within `radius` of the centre | must use the oval's area |
| `setup/mapsetup/RingPicker.java:50` | the look ray's hit within `radius` | must agree with the collision |
| `setup/mapsetup/DraftEditor.java:66` | `withoutRing` rebuilds each ring with `ring.radius()` | would turn an oval into a circle on any removal |
| `setup/adapter/RingPreviews.java:57, 64` | preview scaled `2r x 2r`, rotated from the normal | draws the outer disc until change 3 |
| `race/com/ComPilot.java:204` | detour clearance `radius + DETOUR_CLEARANCE` | keeps the outer bound |
| `platform/catalog/adapter/RingAdapter.java:61` | reads `radius` | must also read the oval fields |
| `platform/catalog/writer/MapDraftJsonWriter.java:144` | writes `radius` | must also write the oval fields |
| `platform/catalog/writer/MapDefinitionCanonicalWriter.java:63` | writes `radius` into the hashed form | must write the oval fields only for an oval |

Other uses of `Ring` do not read the radius: `RacingLine.java:123` and `ComPath.java:131-132` use the centre and the
normal, and `GuideLineRenderer` draws guide points, not a ring outline. No in-game ring outline exists in the code, so the
owner's "in-game ring marker" has nothing to draw until one is designed (open question 2).

The javadoc of `Ring` (`Ring.java:6-8`) describes the hit area as the circumcircle of the ring's corner points. This change
makes the hit area the area of the ring's shape, and the javadoc is rewritten with it (task 3.2).

The file format is read by name in `RingAdapter`, and an unknown key is ignored. A server that predates this change would
therefore read an oval as a circle of its semi-major radius without any error, unless the file's schema version refuses it
first. That is the failure `RingAdapter.java:26-28` prevents for an unknown type (`:63-64` refuses it rather than defaulting).

The current schema version is `SchemaVersion.CURRENT = 2` (`voyager/platform/.../catalog/adapter/SchemaVersion.java:21`),
after `add-catalogue-keys-and-authors`. Its requirements are written against that state: this change's MODIFIED deltas
copy the text that change leaves, and the archive order is add-catalogue first, then this change.

## Goals / Non-Goals

Goals:

- A ring can be an oval, with a semi-major radius, a semi-minor radius and an in-plane major axis.
- Collision and the setup server's picker agree on every ring, circle or oval.
- No file that loads today changes how it plays. Every circle keeps its containment test and its layout hash. A file whose
  radius or points fall outside the bounds of decision 4 is refused; no shipped map is outside them.
- An older server never plays an oval as a circle.

Non-Goals:

- Builder commands to make or change an oval (change 3).
- The map converter, the rim tolerance and the ellipse fit (a separate change).
- An in-game outline of rings, or any new rendering.
- Normalising the sign of the major axis (open question 3).

## Decisions

1. **The ring carries a sealed `RingShape`, and `radius()` stays as the outer bound.** `Ring` replaces its `radius` component
   with a `RingShape shape`. `RingShape` is a sealed interface with two nested final records: `Circle(double radius)` and
   `Ellipse(double radius, double radiusMinor, Vec3 majorAxis)`, where `radius` is the semi-major radius. `Ring.radius()`
   returns `shape.radius()`, so `ComPilot`, the preview and the existing call sites keep their meaning. The six-argument
   constructor stays and builds a `Circle`, so every existing call site compiles unchanged. The file format of decision 5
   is read into this model; the optional fields do not survive into the record.
   *Rejected:* optional `radiusMinor` and `majorAxis` fields beside `radius` on `Ring`. An oval and a circle would then
   share one record with null-or-value rules, and nothing would stop a circle that states a major axis.

2. **Containment is one method on `Ring`, with an exhaustive switch over the shape.** `Ring.contains(Vec3 point)` takes a
   point in the disc's plane. For a `Circle` it is `point.distanceTo(center) <= radius`, the expression already in
   `RingPass.java:44` and `RingPicker.java:50`, so a circle's result is bit-for-bit the same. For an `Ellipse`, with
   `d = point - center`, `u = d · majorAxis`, `v = d · (normal × majorAxis)`, the point is inside when
   `(u / radius)² + (v / radiusMinor)² <= 1`. The rim is inclusive for both.
   *Rejected:* a helper in `race/collision`. The setup server's picker would then depend on `race`, and `voyager/setup`
   depends on `voyager-api` and `voyager-platform` only (`voyager/setup/build.gradle.kts`). A containment method on the api
   record is the one place both `race` and `setup` can reach without a new edge.

3. **Equal radii collapse to a circle in one factory.** `RingShape.of(double radius, double radiusMinor, Vec3 majorAxis)`
   returns a `Circle` when `radiusMinor == radius`, so an oval with equal radii has one representation. It is a static
   method on the interface, following `CatalogKeys` (`voyager/api/.../api/race/CatalogKeys.java`), because
   `DesignRuleTest.apiTypesAreRecordsInterfacesOrEnums` admits no abstract utility class in `voyager-api`. This departs
   from design rule 2's abstract-class form, and the departure is recorded here so a reviewer sees it.

4. **Validity is split by what each record can see, and every radius and point count is bounded.** The owner's bounds
   (decision C4) are: each radius of a ring, the radius and the semi-minor radius of an oval alike, lies in
   `[0.5, 32]` blocks, both ends inclusive; the points of a ring lie in `[0, 100]`.
   - `Circle` refuses a radius outside `[0.5, 32]` with `InvalidRingException.radiusOutOfRange`, which replaces
     `nonPositiveRadius`. The test is written as a negated in-range comparison, so NaN is refused as well.
   - `Ellipse` refuses a `radius` or a `radiusMinor` outside `[0.5, 32]`, a `radiusMinor` greater than `radius`, and a
     `majorAxis` whose length differs from 1 by more than 1e-9.
   - `Ring` refuses points outside `[0, 100]` with `InvalidRingException.pointsOutOfRange`, which replaces
     `negativePoints`. It also refuses an oval whose `majorAxis` has a dot product with the normal of at least 1e-6,
     because only `Ring` has the normal.
   Each refusal is an `InvalidRingException` factory method that names the value and, for a range, the range.
   The bounds are a change for a file: a radius below 0.5 or above 32 no longer loads. The shipped maps have radii from
   3.6 to 7.3 and 10 points per ring, so none of them changes.

5. **The file states an oval as an optional pair, and version 3 is written only for a file that holds an oval.** This is the
   owner's decision (O1). A ring object may state `radiusMinor` and `majorAxis`, and must state both or neither. `radius`
   is always the semi-major radius. A file whose schema version is below 3 that states either field is refused, and the
   message names the file, the ring's position, the field and the version it needs. A file that states version 3 loads with
   or without ovals. A draft writes version 3 when one of its rings is an oval, and version 2 otherwise. See ADR-0043.

6. **A circle-only file is not touched by the layout hash.** `MapDefinitionCanonicalWriter` writes `radiusMinor` and
   `majorAxis` only for an oval. A circle keeps the exact bytes it has today, so `LayoutVersion` does not change for a
   circle-only map, and no leaderboard resets. A map with an oval has a different canonical form and a different layout
   version. That reset is intended, because the course is different.

7. **The setup server keeps the shape of every ring it rebuilds.** `DraftEditor.withoutRing` passes `ring.shape()` where it
   now passes `ring.radius()` (`DraftEditor.java:66`). Without this, removing any ring would turn every oval after it into
   a circle, and the file would change without the builder touching those rings. `RingPicker` uses `Ring.contains`.

8. **The builder's preview draws an oval's outer disc until change 3.** `RingOrientation` has no in-plane rotation, so an
   oval cannot be drawn with its axis yet. The preview keeps `ring.radius()`, the semi-major radius, which is the outer bound
   of the oval. This is an interim state, and the proposal's out-of-scope list names it.

9. **Computer racers keep the outer bound as clearance, and no code changes.** `ComPilot.java:204` uses `ring.radius()`,
   which is now the semi-major radius. That is the bound a detour must clear. A test pins it (task 1.11).

10. **Schema version 3 for both kinds of file, and cups are still written at version 2.** The highest version the server reads
    becomes 3 for maps and cups alike, because the rule of `catalog/file-versioning` is shared, and `map.schema.json` and
    `cup.schema.json` describe version 3. `map-v2.schema.json` and `cup-v2.schema.json` keep the version 2 documents,
    following `map-v1.schema.json` in add-catalogue. The writers declare version 3 only for a file that holds an oval
    (decision 5). A cup has no ring, so no cup is written at version 3, and the cup writer keeps `schemaVersion` 2.

## ADR-0043 (proposed): Oval rings are an optional ring shape in a version 3 file

Status: Proposed, on 2026-10-29, with the change `oval-ring-model`. The format is the owner's decision. The record moves to
Accepted when the owner approves it, as ADRs require.

Number: 0043 is the next number after ADR-0042, which change 1 (`import-finished-world`) takes. ADR-0040 is taken by
`add-catalogue-keys-and-authors`, and ADR-0041 exists.

### Context

A ring is a disc, and the owner decided that a ring may be an oval. The file format must state the oval without breaking
the servers that read it. An older server ignores a key it does not know, so a new key on a ring would be read as a circle
of the semi-major radius, silently. The schema version is the existing way for a file to say that it needs a newer reader,
and `catalog/file-versioning` refuses a file that states a version above the server's maximum.

### Decision drivers

- A server that predates the change must refuse an oval, never play it as a circle.
- A circle keeps its file, its hash and its behaviour.
- The API keeps one ring model, with no nullable field sets.

### Decision

The file states an oval with two optional ring fields, `radiusMinor` and `majorAxis`, which must come together, and a file
that holds an oval states schema version 3 (option A; owner decision O1). A file that holds no oval keeps its version.

*Rejected:* a `shape` discriminator (`"CIRCLE"` or `"ELLIPSE"`) with the fields of the shape, because every circle would then
change its file to carry a new key.

*Rejected:* the optional fields without a version bump, because a server before this change would read each oval as a
circle of its semi-major radius with no refusal.

### Consequences

- Good: an older server refuses an oval with the file, position and version named; it does not play a wrong course.
- Good: circle-only files and their layout hashes do not change.
- Good: `radius` keeps its meaning, so every existing reader sees the outer bound.
- Bad: the game servers must be deployed before any setup server writes an oval. This is an operator step.
- Bad: a file with a version 3 declaration is refused by any server built before this change, including for a map that has
  no oval. The writer avoids this by writing version 3 only for an oval.

### Confirmation

`RingShapeTest` and `RingAdapterTest` pin the refusal and the containment; `MapDraftJsonWriterTest` pins the version a draft
writes; the characterization test of task 2.1 pins the layout hash of every shipped map, which must not change.

## Dependency direction

```
voyager-api (RingShape, Ring, InvalidRingException)
   ^               ^                 ^
   |               |                 |
voyager-race   voyager-platform   voyager-setup (RingPicker, DraftEditor)
(RingPass)     (RingAdapter, writers, SchemaVersion, schemas)
```

`RingShape` and the containment live in `net.elytrarace.voyager.api.race`, the innermost ring, with no I/O and no Minestom or
Adventure types (`ApiPurityTest`). Race and platform depend on api. Setup depends on api and platform, as it does now, and
it does not depend on race. No new dependency edge appears. Each new boundary is covered:

- The api containment and the shape validity: `ApiPurityTest` (no I/O, no platform types) and `DesignRuleTest`
  (`apiTypesAreRecordsInterfacesOrEnums`, which admits the records and the interface).
- The permitted shapes: one new rule in `DesignRuleTest`, `ringShapeIsSealedToCircleAndEllipse`. It requires that the classes
  that implement `RingShape` are exactly `Circle` and `Ellipse`, and that both are final records. It is a new rule, so it gets
  a seeded violation in the same way `ApiPurityRulesSeededTest` seeds its rules, and `allowEmptyShould(false)` applies.
- The picker's domain: `ApiPurityTest.setupDomainOnlyDependsOnApiAndJdk` covers `setup.mapsetup`, which is why the picker
  calls `Ring.contains` and does not reach into `race`.

## Testing

All tests follow F.I.R.S.T. and "Red/Green TDD", with a failing test before each behaviour.

- Containment tests are plain JUnit in `voyager-api`. They construct rings directly, read no clock, touch no file and use
  fixed points. The circle test compares `Ring.contains` with the existing expression on a fixed grid, so a change in
  the circle's behaviour fails the test.
- Points on the rim are chosen exactly on an axis, where the arithmetic is exact (for example `u = radius`, `v = 0`), so the
  inclusive rim is tested without a tolerance. Tilted cases use points clearly inside or outside, so they do not depend on
  rounding.
- The bound tests use the values 0.5 and 32, which are inside, and values just outside them, such as 0.49 and 32.01.
- The adapter and writer tests use `@TempDir` for every file. They read and write files in the temporary directory only.
- The characterization test (task 2.1) reads the shipped maps from the classpath and pins their layout versions. It is
  written and passes before any production change, so a regression in the circle path is caught.
- The schema tests validate committed fixtures in a temporary directory against the schema documents on the classpath.
- No test sleeps, reads the system time, or shares static state. No test reaches the network.
- The Red run for each test must fail for the stated reason: an oval is read as a circle, a refusal is missing, a bound is
  not enforced, or a containment result differs.

## Open questions

1. **Leaderboards of converted maps.** A map that becomes an oval gets a new layout version, so its leaderboard starts again.
   Recommendation: accept this, because the course changes. The owner confirms it before the old maps are converted, which
   is a later change.
2. **An in-game ring outline.** The code draws no ring outline today; the builder's preview is the only ring shape on screen.
   Recommendation: add none in this change; the preview of change 3 is the place for the oval's shape. The owner confirms that
   the in-game marker of the handoff is not needed yet.
3. **Sign of the major axis.** The same ellipse has two valid axes, `m` and `-m`, and so two canonical forms and two layout
   versions. Recommendation: the writer normalises the axis so that its first non-zero component is positive, and the
   reader does not require it. This is a small rule; the owner confirms it.
4. **Stale requirement of the schema spec.** `openspec/specs/catalog/file-schema` holds the requirement "The schema still
   describes the current version only", which caps `schemaVersion` at 1. `add-catalogue-keys-and-authors` modifies the other
   cap requirement, not this one, so the two conflict after that change is archived. This change modifies the stale requirement
   so that both name the current version. The owner or the archive of add-catalogue should confirm this reading.
