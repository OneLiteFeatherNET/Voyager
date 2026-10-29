# Design

## Context

The setup server appends and removes rings through the wand. A placed ring takes the builder's eye as its centre, the look
direction as its normal, and `RingDefaults` for everything else: radius `sqrt(13)`, ten points, type `STANDARD`
(`voyager/setup/.../mapsetup/RingFromPose.java:27-34`, `RingDefaults.java:17-21`). `WandListener.place` and `WandListener.remove`
are the only writers of a draft's rings (`voyager/setup/.../adapter/WandListener.java:85-133`). `SetupCommands` has no ring
subcommand (`SetupCommands.java:71-78`).

`DraftEditor` makes exactly three edits: append, remove, and set the spawn (`DraftEditor.java:33-74`), and its javadoc says so
(`DraftEditor.java:13-16`). Each returns a new draft.

The look ray is built in `WandListener`: the eye is the position plus the eye height (`WandListener.java:162-164`) and the
direction is the position's direction (`:94`, `:122`). The ring target is `RingPicker.nearestCrossed`, which takes the eye,
the direction and the reach `RingDefaults.REACH_BLOCKS` = 32 (`RingPicker.java:31-59`). After `oval-ring-model`, the picker
calls `Ring.contains`, so it agrees with collision.

The preview draws each ring as one block display scaled `2r x 2r x 0.05` and turned by `RingOrientation.fromNormal`
(`voyager/setup/.../adapter/RingPreviews.java:56-70`, `voyager/setup/.../mapsetup/RingOrientation.java:41-49`). That
rotation fixes the disc's plane but not the direction of its X axis, so an oval cannot be drawn with its axes yet.

`oval-ring-model` supplies the model this change edits: `Ring` carries a sealed `RingShape` with the final records `Circle(radius)`
and `Ellipse(radius, radiusMinor, majorAxis)`, where `radius` is the semi-major radius; `Ring.radius()` returns the semi-major
radius, and `RingShape.of(radius, radiusMinor, majorAxis)` returns a `Circle` when the two radii are equal. The names here are
those of that change's design; if they change there, this design follows.

## Goals / Non-Goals

Goals:

- Each ring field a builder needs (type, radius, both oval axes, the oval's turn, points) can be edited on the ring the builder
  looks through, with one command each.
- The edits are pure functions over the draft, so the rules are tested without a server.
- A refused edit changes nothing, and a made edit is saved, previewed and undoable like a placement.

Non-goals: see `proposal.md`. In short: no move, no normal change, no insert or reorder, no new default for the wand.

## Decisions

1. **The look-ray target is the wand's target.** Each `/map ring` subcommand asks `RingPicker.nearestCrossed` with the eye and
   direction of the builder and the reach `RingDefaults.REACH_BLOCKS`, exactly as the wand's removal does. The eye and
   direction move into a package-private `LookRay` helper in `setup/adapter`, which `WandListener` and `SetupCommands` both
   call. `WandListener`'s behaviour does not change; `WandListenerTest` pins it. Rejected: a second copy of the ray maths in
   `SetupCommands`, which would let the wand and the commands target different rings.

2. **The pure edits live in `RingEdits`, and the draft write in `DraftEditor`.** `RingEdits` is an abstract utility class in
   `net.elytrarace.voyager.setup.mapsetup`, with a private constructor, `@ApiStatus.Internal` and `@Contract(pure = true)`
   on each method, as `DraftEditor` is. Its methods are `withType`, `withPoints`, `withRadius`, `withOval`, `withCircle` and
   `withTurn`. Each builds the changed `Ring` and passes it to a new `DraftEditor.withRingReplaced(draft, index, ring)`, which
   checks that the index is in range and that the new ring keeps the index. The new method goes through the same private
   `with` as the other edits, so the display name and the authors survive it once `keep-draft-metadata-on-edit` has fixed
   `with`. Rejected: putting the shape arithmetic into `DraftEditor`, which would then hold more than the three edits its
   javadoc names.

3. **Each command states its rule, and a refusal names the value.** The rules are:
   - `type`: one of `RingType`'s names; the match is case-insensitive; an unknown name is refused with the list of names.
   - `radius <r>`: on a circle it sets the radius. On an oval it sets the semi-major radius and keeps the minor radius; a value
     below the minor radius is refused, so the minor radius never shrinks by itself.
   - `oval <a> <b>`: `b <= a` is required, otherwise refused. Equal values give a circle, as `RingShape.of` does. An existing
     oval keeps its major axis; a circle gets the default major axis of decision 5.
   - `circle`: drops the minor radius and the major axis; the radius stays the semi-major radius.
   - `tilt <degrees>`: refused on a circle, with the message to use `oval` first. On an oval it sets the major axis to the
     default axis turned by the angle (decision 6).
   - `points <n>`: an integer from 0 to 100.
   A value outside the bounds of decision 4 is refused before any ring is built, with `InvalidRingEditException`
   (`api/mapsetup/exception`), whose factories name the field, the value and the bound.

4. **Bounds.** Radius and both oval axes lie between 0.5 and 32 blocks inclusive, for a circle and for an oval. The upper bound is
   the reach of the picker, so a ring a builder can make is one the picker can reach from its own position. Points are the
   points of a ring and lie between 0 and 100 inclusive. A value outside these bounds is refused before any ring is built.

5. **The default major axis is fixed by one rule.** `RingEdits.defaultMajorAxis(normal)` projects the world's up axis
   `Y = (0, 1, 0)` into the ring's plane: `Y - (Y · n) n`, normalised. When that projection has length below `1e-9`, which is
   when the normal is parallel to Y and the ring lies flat, it returns world X `(1, 0, 0)`, which is perpendicular to such a
   normal. For a ring whose normal is horizontal, the default axis is vertical, so a new oval is a tall ring, as the course
   builders expect.

6. **The turn is absolute.** `withTurn(draft, index, degrees)` sets the major axis to
   `cos θ · a0 + sin θ · (n × a0)`, where `a0 = defaultMajorAxis(n)` and `θ` is the angle in radians. The rule is a rotation
   about the normal, and positive is counter-clockwise when seen from the normal's tip. Because it is absolute, a second turn
   replaces the first and does not add to it. The angle may be any finite number. Rejected: a relative turn, which drifts when
   a builder repeats a command. The turn needs no stored angle: the axis itself is stored, and the default axis is a function
   of the normal, which no command changes.

7. **The preview shows an oval as its bounding block, turned to the major axis.** `RingOrientation.fromMajorAxis(normal, major)`
   returns the unit quaternion of the frame whose columns are `major`, `normal × major` and `normal`, so that the block's X
   axis lies along the major axis and its Z axis along the normal. Circles keep `fromNormal`, so every circle preview is drawn
   as before. `RingOrientation.discOffset` gains a two-axis overload `discOffset(q, a, b, thickness)`, which the one-radius
   form calls with `a = b = radius`, so a circle's offset is unchanged. `RingPreviews` scales an oval to `2a x 2b x 0.05`.
   The block is a rectangle, not the ellipse; open question 1 asks whether that is acceptable.

8. **Every edit goes through `MapSession.commit`.** A command that changes a ring calls `commit` with the new draft, so the
   save comes first, the previews are shown, and `undo-draft-edits` records the previous draft. A command that changes nothing
   (the same value, or the circle `circle` on a circle) does not save and says so with `voyager.setup.ring.unchanged`.

9. **The ring commands and the wand share the gate.** Each `/map ring` subcommand runs through `SetupCommands.gated`, so a builder
   without `voyager.setup.use` changes nothing, as for every other subcommand (`SetupCommands.java:85-91`).

10. **The file form of an oval.** An oval is saved with the optional ring fields `radiusMinor` and `majorAxis` of
    `oval-ring-model`. Only a draft that holds an oval is written as schemaVersion 3; a draft of circles stays at version 2.
    A server that reads only up to version 2 refuses a version 3 file, and a version 2 file that states an oval field is
    refused. This change adds no check of its own: the setup server writes what the builder made. The deployment order (game
    servers first) is an operator step recorded in `oval-ring-model`.

## Dependency direction

- `api`: `InvalidRingEditException` in `net.elytrarace.voyager.api.mapsetup.exception`. It uses `Ring` and `RingShape` from
  `oval-ring-model` and changes neither.
- `setup/mapsetup`: `RingEdits` (new), `DraftEditor.withRingReplaced` (new), `RingOrientation.fromMajorAxis` and the two-axis
  `discOffset` (new). They depend on `api` and `java.util` only.
- `setup/adapter`: `SetupCommands` (the `ring` subcommands), `RingPreviews` (ovals), `LookRay` (new, extracted from
  `WandListener`). They depend inward on `setup/mapsetup` and `api`, as now.
- `platform/text`: the messages of decision 3 and the list of `RingType` names.
- No new edge between slices. `race` and `platform` are not touched.

Fitness: `ApiPurityTest.setupDomainOnlyDependsOnApiAndJdk` covers `RingEdits`, `DraftEditor` and `RingOrientation`, so they
cannot import Minestom. `DesignRuleTest` covers the new exception's name and hierarchy. `ApiPurityTest` keeps the new api
exception free of I/O. `MessageBundleParityTest` checks the English and German keys. No new fitness rule is proposed.

## Testing

Every test follows F.I.R.S.T.

- `RingEditsTest` (plain JUnit, `setup/mapsetup`): fixture rings with fixed centres and normals; no clock, no file, no random
  values. The default axis is checked for a flat ring and a wall ring. Known turns are checked for normal `(0, 0, 1)`: a turn
  of 0 degrees gives `(0, 1, 0)` and a turn of 90 degrees gives `(-1, 0, 0)`.
- `RingOrientationTest`: `fromMajorAxis` maps the block's X axis to the major axis and its Z axis to the normal, for turns of 0, 45
  and 90 degrees. The two-axis `discOffset` with `a = b = r` equals the one-radius form.
- `RingPreviewTest` (`@EnvTest`, one `Env` per test, `env.tick()`): an oval preview has scale `2a x 2b` and the X axis of its
  rotation along the major axis.
- Minestom command tests: `/map ring type boost` saves and replies; `tilt` on a circle is refused and the file keeps its bytes;
  a ring command with no ring in reach changes nothing; an edit that changes nothing writes nothing; a builder without the node
  changes nothing.
- All temporary files live in `@TempDir`. No test sleeps, reads the system time or shares static state.

## Open questions

1. **Preview of an oval.** A block display is a rectangle of `2a x 2b`, which contains the ellipse and also its four corners,
   so a builder sees a larger area than the racer must fly through. Options: (a) the bounding rectangle, as the brief decided;
   (b) an outline of thin block displays along the rim, which shows the true shape at a cost of several entities per oval.
   **Recommendation: (a) for this change.** The corners are outside the collision area, and the rectangle is only a visual aid.
   If builders misjudge the short axis, option (b) is a follow-up. Owner decision.

2. **The name of the turn command.** The brief fixes `/map ring tilt`. Options: (a) keep `tilt`, and say "turn" in the messages;
   (b) rename it to `turn`. **Recommendation: (a),** because the brief names the command, and the messages remove the
   confusion with a tilted ring. Owner decision.

3. **Remembering the last type and size.** The brief lists it. **Recommendation: no.** A command states its value, so the
   builder never edits a ring with a value the builder did not type. Owner decision.

4. **Landing the circle-only commands before change 2.** The brief allows `type`, `points` and `radius` on a circle to land
   after `undo-draft-edits`, before `oval-ring-model`, because they need only the circle that exists today. Options: (a) one
   pull request after change 2, as the tasks are written; (b) a first pull request with the circle-only commands, and a second
   with the oval commands, the oval preview and the turn. **Recommendation: (b),** because the builder gets the type command
   sooner and the second pull request is small. It needs the owner's choice, and the second pull request keeps the same title
   scope. Owner decision.
