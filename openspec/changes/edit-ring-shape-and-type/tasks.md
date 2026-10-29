# Tasks

Commit types and scope: every behaviour ships as `feat(setup)`. `docs` for the how-to guide. `test` only for a test-only commit
that changes no behaviour. Each behaviour gets a failing test (Red) before its production code (Green). This change starts only
after `undo-draft-edits` (change 4) and `keep-draft-metadata-on-edit` (change 6) are merged, and the oval commands (`oval`, the
`circle` of an oval, `tilt`, and the oval preview) start after `oval-ring-model` (change 2) is merged. The circle-only commands
(`type`, `points`, and `radius` on a circle) may land first, as a pull request under the same title, because they need no oval
model; the brief allows this and the owner decides it (`design.md`, open question 5). Every test follows F.I.R.S.T.: plain JUnit for `RingEdits` and
`RingOrientation`, a fresh `Env` per Minestom test, `@TempDir` for files, fixed fixtures, no sleeps, no system-time reads, no
shared static state. The open questions in `design.md` are answered by the owner before section 2 starts. Task checkboxes are
ticked in the follow-up pull request, never in the code pull request.

## 1. Red: the pure edits, the orientation and the commands

- [ ] 1.1 Red: `RingEditsTest` asserts that `withType` and `withPoints` keep the index, the centre and the normal, and that 101 points are refused (`feat(setup)`)
- [ ] 1.2 Red: `RingEditsTest` asserts that a radius of 0.49 and of 32.01 are refused, and that 0.5 and 32 are accepted (`feat(setup)`)
- [ ] 1.3 Red: `RingEditsTest` asserts that `withRadius` on an oval sets the semi-major radius, and refuses a value below the semi-minor radius without changing the minor radius (`feat(setup)`)
- [ ] 1.4 Red: `RingEditsTest` asserts that `withOval(3, 3)` yields a circle, and that a minor radius above the major radius is refused (`feat(setup)`)
- [ ] 1.5 Red: `RingEditsTest` asserts that `withCircle` on an oval keeps the semi-major radius as the radius and drops the minor axis (`feat(setup)`)
- [ ] 1.6 Red: `RingEditsTest` asserts that `withTurn` on a circle is refused, and that on an oval with normal `(0, 0, 1)` a turn of 0 degrees gives `(0, 1, 0)` and a turn of 90 degrees gives `(-1, 0, 0)` (`feat(setup)`)
- [ ] 1.7 Red: `RingEditsTest` asserts that the default major axis is world X for a normal parallel to Y and the projection of Y otherwise (`feat(setup)`)
- [ ] 1.8 Red: `DraftEditorTest` asserts that `withRingReplaced` keeps the index, refuses a ring with another index, and keeps the display name and the authors (`feat(setup)`)
- [ ] 1.9 Red: `RingOrientationTest` asserts that `fromMajorAxis` maps the block's X axis to the major axis and its Z axis to the normal, for turns of 0, 45 and 90 degrees, and that the two-axis `discOffset` with equal axes equals the one-radius form (`feat(setup)`)
- [ ] 1.10 Red: `RingPreviewTest` asserts that an oval's preview has scale `2a x 2b` and a rotation whose X axis lies along the major axis (`feat(setup)`)
- [ ] 1.11 Red: Minestom test (`@EnvTest`): `/map ring type boost` saves the ring as `BOOST`, replies with the new type, and the file holds `BOOST` (`feat(setup)`)
- [ ] 1.12 Red: Minestom test: `/map ring tilt 30` on a circle is refused with the message to use `oval` first, and the file keeps its bytes (`feat(setup)`)
- [ ] 1.13 Red: Minestom test: a `/map ring` subcommand with no ring in reach replies the none message and writes no file (`feat(setup)`)
- [ ] 1.14 Red: Minestom test: an edit that changes nothing replies the unchanged message and writes no file (`feat(setup)`)
- [ ] 1.15 Red: `SetupPermissionTest`: a builder without `voyager.setup.use` cannot run `/map ring type`, and the draft file keeps its bytes (`feat(setup)`)
- [ ] 1.16 Red: confirm 1.1 to 1.15 fail for the stated reason, and record the failure messages in the code pull request (`feat(setup)`)

## 2. Green: the edits, the preview and the commands

- [ ] 2.1 Green: `InvalidRingEditException` (final, `RuntimeException`) in `net.elytrarace.voyager.api.mapsetup.exception` with factories that name the field, the value and the bound; `package-info.java` already present (`feat(setup)`)
- [ ] 2.2 Green: `DraftEditor.withRingReplaced(draft, index, ring)`, through the private `with`, so the metadata is kept (`feat(setup)`)
- [ ] 2.3 Green: `RingEdits` with `withType`, `withPoints`, `withRadius`, `withOval`, `withCircle`, `withTurn` and `defaultMajorAxis`, and the bounds of `design.md` decision 4 (`feat(setup)`)
- [ ] 2.4 Green: `RingOrientation.fromMajorAxis(normal, major)` and the two-axis `discOffset`; the one-radius form delegates to it (`feat(setup)`)
- [ ] 2.5 Green: `RingPreviews` draws an oval at `2a x 2b` with the in-plane rotation; circles keep `fromNormal` (`feat(setup)`)
- [ ] 2.6 Green: `LookRay` extracted from `WandListener` (eye and direction), with `WandListener` calling it; `WandListenerTest` unchanged (`feat(setup)`)
- [ ] 2.7 Green: the `/map ring` subcommands in `SetupCommands` (`type`, `radius`, `oval`, `circle`, `tilt`, `points`), each gated, each targeting the look-ray ring through `RingPicker.nearestCrossed`, each saving through `MapSession.commit` (`feat(setup)`)
- [ ] 2.8 Green: messages `voyager.setup.ring.type.set` (arguments: index, type), `voyager.setup.ring.type.unknown` (arguments: the input, the list of names), `voyager.setup.ring.radius.set` (index, radius), `voyager.setup.ring.oval.set` (index, major, minor), `voyager.setup.ring.circle.set` (index), `voyager.setup.ring.turn.set` (index, degrees), `voyager.setup.ring.points.set` (index, points), `voyager.setup.ring.unchanged` (index) and `voyager.setup.ring.refused` (the reason), in `SetupMessages` (`platform/text`) and in `voyager_setup_en_US.properties` and `voyager_setup_de_DE.properties`, the German in informal "du" (`feat(setup)`)

## 3. Refactor and docs

- [ ] 3.1 Refactor: check that the wand and the `/map ring` commands both target through `RingPicker.nearestCrossed`, with no second copy of the look-ray maths; change no behaviour (`feat(setup)`)
- [ ] 3.2 Add a section "Edit a ring" under "Place and remove rings with the wand" in `docs/guides/how-to-build-a-map-with-setup.md`, with the six commands, the bounds, the meaning of "turn" against a tilted ring, and the note that the preview of an oval is its bounding block (`docs`)
- [ ] 3.3 Add to the same guide that a map with an oval needs the game server built with `oval-ring-model` before it is played (`docs`)

## 4. Verify

- [ ] 4.1 Run `./gradlew :voyager:setup:test :voyager:fitness:test` and confirm `setupDomainOnlyDependsOnApiAndJdk`, `DesignRuleTest` and `MessageBundleParityTest` pass (`test`)
- [ ] 4.2 Run `./gradlew build` on both trees (`test`)

## 5. Ship

- [ ] 5.1 Open the code pull request under the title `feat(setup): set ring type, radius and oval shape`. Fill the sections of `.github/pull_request_template.md`, tell the story of a builder who wants a checkpoint and an oval without placing the ring again, list the commands and the failing tests of 1.16, state that the oval preview is a bounding block, and end with the referral line of the repository's pull request rule. Do not add a session link (`feat(setup)`)
