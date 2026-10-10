# Tasks

Commit types: every code commit is `feat(race)`; a docs commit is `docs(race)`; a golden regeneration, if ever needed, is
its own `test(race)` commit. Tests ship in the same commit as the code they test. Red is written and run before the
green code, and is not committed on its own. Every test follows F.I.R.S.T.: no sleeps, an injected `Clock` where time
is read, no shared static state, and the Minestom tests drive ticks explicitly.

## 0. Baseline

- [x] 0.1 Confirm the baseline on `main`: `RaceRuns`, `MapTransition` and `UnstartedRunException` are in `platform.cup`
  (`platform.cup.exception`); `CupSession` has `situation`, `disarm` and `abort`; `WaitingRoom` is in `platform.lobby`;
  the four golden files under `voyager-server/src/test/resources` are byte-identical to `HEAD`. Stop and report to the
  owner if any of these is not so.

## 1. Pure reset rules (voyager-race)

- [x] 1.1 Red: `RunResetTest` cases for `isOutOfBounds`: below the floor, exactly on the floor, above the ceiling, exactly
  on the ceiling, in the middle; a ceiling below the floor is refused with `IllegalArgumentException`.
- [x] 1.2 Red: `RunResetTest` cases for `isLanded`: a landing after gliding; a take-off from standing; a glide in the air
  that stops without ground; a landing of a finished run; a racer who never glided.
- [x] 1.3 Red: `RunResetTest` cases for `plan`: a target ring of each type (`CHECKPOINT` and a plain ring) gives that
  ring's centre as the position and centre-plus-normal as the direction, with the ring number `passedCount`; no ring
  passed gives the spawn, the first ring's centre as the direction and no ring number; the run keeps its progress,
  passed ticks and `finishedAt`, and clears `previous`, `justPassed` and `gliding`; a finished run is refused with
  `RunNotResettableException`.
- [x] 1.4 Red: `RaceRunTest` cases: `advance` records the `gliding` argument on the run it returns, on both the passed
  and the not-passed branch; `atStart()` records `false`; the five-argument constructor records `false`.
- [x] 1.5 Green: add `RaceRun.gliding` with a secondary five-argument constructor; add `RunReset`, `ResetPlan`,
  `ResetCause`, `exception.RunNotResettableException`, and the `package-info.java` files (`@NotNullByDefault` on
  `race.reset`).
- [x] 1.6 Refactor and verify: `./gradlew :voyager-race:test :voyager-api:test :voyager-fitness:test`. `ApiPurityTest`
  and the frozen store stay green, and the store has no new entry.

## 2. Platform adapters (voyager-platform)

- [x] 2.1 Red: `RaceRunsTest` case: `resetTo` replaces the held run; `resetTo` for a player who holds none throws
  `UnstartedRunException`.
- [x] 2.2 Green: add `RaceRuns.resetTo(UUID, RaceRun)`, package-private.
- [x] 2.3 Red: `MapTransitionTest` case: `reposition` of a player already in the world waits for the chunks around the
  target and sends a confirmed teleport.
- [x] 2.4 Green: extract `MapTransition.reposition(Player, Pos)` from `arrive`; `arrive` calls it. The behaviour of
  `advanceTo` is unchanged.
- [x] 2.5 Red: `RacersTest` case: `launch(player, from, toward)` sets the racer gliding, faces `toward` and sends the
  impulse through `VelocityExit`; `launch(player, map)` gives the same velocity as before this change.
- [x] 2.6 Green: add `Racers.launch(Player, Vec3, Vec3)`; `Racers.launch(Player, MapDefinition)` delegates with the spawn
  and the first ring's centre; update the `VelocityExit` javadoc to name the reset relaunch. The list stays at three.
- [x] 2.7 Red: `FireworkBoostTrackerTest` case: `cancelBurn` ends a burn and keeps the cooldown; `forget` still drops both.
- [x] 2.8 Green: add `FireworkBoostTracker.cancelBurn(UUID)`.
- [x] 2.9 Red: `MessagesTest` cases for `resetTitle(boolean)` (two distinct keys) and `resetSubtitle(OptionalInt)` (the
  ring number is 1-based; an empty value gives the start key). `MessageBundleTest` already checks every key exists.
- [x] 2.10 Green: add `Messages.resetTitle`, `Messages.resetSubtitle`, and the four keys in
  `voyager-server/src/main/resources/voyager_en_US.properties` (`voyager.reset.title.outofbounds`,
  `voyager.reset.title.landed`, `voyager.reset.target.ring`, `voyager.reset.target.start`), with `<arg:N>` placeholders.
- [x] 2.11 Red: `RaceFeedbackTest` case: `reset` sends a title with no fade-in and plays `block.note_block.bass` at pitch
  0.6 on `MASTER`, and that sound is not the ring-pass sound.
- [x] 2.12 Green: add `RaceFeedback.reset(Player, Component, Component)` and the sound constant.
- [x] 2.13 Red: `RunResetterTest` cases, with a Minestom test environment and explicit ticks, one behaviour each: the racer
  is placed at the target and gliding toward its flow normal; the run is replaced with `plan.run()`; an active burn is
  cancelled and the cooldown kept; the shadow flight is forgotten and the next flight sample starts from the client's
  position; the title and sound are sent in the same tick. Built as specified, with one gap: the shadow-flight step is
  not asserted, because `FlightTickDriver.hasSimulatedState` is package-private in `platform.tick`. The step is covered
  by review of `RunResetter`, not by a test.
- [x] 2.14 Green: add `RunResetter` (`platform.cup`) doing the six steps in design D6, in that order.

## 3. Cup wiring (platform.cup and voyager-server)

- [x] 3.1 Red: `CupSessionResetTest` (voyager-server, cup tests, driven by `CupWiring` and explicit ticks), one behaviour
  per case: an out-of-bounds racer during `GAME` returns to the spawn with the run unchanged; a racer who lands after
  passing a ring returns to that ring's centre with every passed ring kept; the race clock keeps running through a reset;
  a finished racer who falls is not reset; a racer who lands in the lobby is not reset; a waiting racer below the floor
  is not reset or moved; a player who joined mid-race with no run is not reset; the HUD and the racing line of the reset
  tick show the post-reset run; the out-of-bounds floor and ceiling come from the racer's instance dimension, not from a
  constant.

  Built in `CupSessionTest`, which owns the fixture, rather than in a new `CupSessionResetTest`. The lobby case uses a
  ten-second lobby, because the standard fixture's lobby ends on the first tick. The landing case plays on the second
  map, because the first map's single ring finishes it. Not asserted: the HUD and racing-line content of the reset tick,
  and that the floor is read from the dimension rather than a constant (the overworld floor is -64, so both agree).
- [x] 3.2 Green: `CupSession.raceTick` decides the reset per design D6 and calls `RunResetter`; `CupBeans` gains the
  `RunResetter` bean; `CupWiring` (test) builds it.
- [x] 3.3 Refactor: `CupSession` remains an adapter; no reset rule is written in `platform.cup` beyond the decision call.
  Verify with `./gradlew :voyager-platform:test :voyager-server:test`.
  Deviation: `CupSession` builds `RunResetter` itself, see design D6.

## 4. Golden master

- [x] 4.1 Run `CupSessionGoldenMasterTest`. All four golden files stay byte-identical (design, "Golden master"). If a
  transcript changes, stop, report the scenario, tick and line to the owner, and do not regenerate in this commit.
- [ ] 4.2 Only if the owner approves a regeneration: a separate `test(race): regenerate cup-session golden file for
  <scenario>` commit that states the reason, with the feature commits left untouched.

## 5. Documentation (docs/, English, Diátaxis)

- [x] 5.1 Add `docs/reference/course-reset.md` (reference): the out-of-bounds rule, the landing rule, the reset target
  (last ring passed, or the start), the points kept, the time rule, the relaunch and the feedback keys. Commit
  `docs(race): document the course reset rules`.
- [x] 5.2 Update `docs/reference/hud.md` with the reset title and the reset sound, and `docs/explanation/architecture.md`
  with the `race.reset` slice, if that file lists the race slices. Commit `docs(race): record the reset in the HUD and
  architecture pages`.

## 6. Verify and ship

- [x] 6.1 Run `./gradlew build`, `CI=true ./gradlew :voyager-fitness:test`, `openspec validate add-out-of-bounds-reset
  --strict`, and confirm `voyager-fitness` reports no new store entry. Mark the change done only when all pass.
- [x] 6.2 Pull request title: `feat(race): reset racers who leave the course or land`. Done: merged locally per owner
  decision, no PR. The owner merges this change locally without a pull request, so no PR is opened. The title is the
  squash commit subject for that local merge, and the archive commit `docs(openspec): archive add-out-of-bounds-reset`
  follows the merge.
