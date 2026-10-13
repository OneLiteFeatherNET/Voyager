# Tasks

Conventional Commits: code commits are `feat(race): ...`; docs commits are `docs(race): ...` or `docs(decisions): ...`; the
archive commit is `docs(openspec): archive checkpoint-resets`. Each commit carries one type. Red before Green for every
behaviour (Red/Green TDD). Every test follows F.I.R.S.T.: an `Env` or a plain fixture with explicit ticks, no sleeps, no
wall-clock reads, no shared static state, each test builds its own fixture.

## 1. Owner decisions (gates; no code before 1.1 to 1.3 are answered)

- [x] 1.1 Owner confirms design D2: a reset keeps the rings up to its target and removes the rest, which amends "A reset
  keeps every ring already passed". Owner chooses the committed-map option (a) accept, (b) add checkpoints later, or (c)
  rejected. Record the answer in design.md under "Decisions recorded from the owner".
  Answer (2026-10-13): D2 accepted, option (a) accept. Later maps get `CHECKPOINT` rings in a separate step (design, "Decisions recorded from the owner").
- [x] 1.2 Pulse (`voyager-game-psychologist`) reviews D2 and the restart of a racer on a map with no checkpoint, and the
  landing grace (R1, R2). Record the review in design.md. Human in the loop: the owner decides on the review's findings.
  Answer (2026-10-13): the owner decided D2 and the grace on 2026-10-13, so the Pulse review is replaced by the owner's decision and the playtest tasks 6.2 to 6.4.
- [x] 1.3 Owner chooses the spawn reset: S1 (as at GO, recommended) or S2 (a lifted spawn with a glide), and whether the
  deferred flag F2 is acceptable as the fallback (design D3).
  Answer (2026-10-13): S1 (a spawn reset behaves like GO); F2 (deferred glide flag) accepted as the fallback.
- [x] 1.4 Owner confirms the seeds `N = 20` airborne ticks and `C = 40` cooldown ticks (design D4).
  Answer (2026-10-13): confirmed, N = 20 airborne ticks and C = 40 cooldown ticks.
- [x] 1.5 Owner confirms ADR 0031 as a new record that amends ADR-0028 (design D8). The agent never marks an ADR Accepted.
  Answer (2026-10-13): confirmed, ADR-0031 amends ADR-0028. It stays Proposed until the owner playtests it.

## 2. Red: behaviour tests (`voyager/race` first, then `voyager/platform` and `voyager/server`)

Race slice (pure; `RunResetTest`, `RaceRunTest`, `ProgressTrackerTest`):

- [x] 2.1 Red: `RunResetTest.aResetTargetsTheLastCheckpointPassed`: with rings 0 to 9 passed and checkpoints at 2 and 6,
  the plan's position is ring 6's centre and its facing is one step along ring 6's normal. Fails today: the target is ring 9.
- [x] 2.2 Red: `RunResetTest.aResetWithNoCheckpointTargetsTheSpawn`: with rings 0 to 9 passed and no checkpoint, the plan
  targets the spawn and faces ring 0's centre. Fails today: the target is ring 9.
- [x] 2.3 Red: `RunResetTest.aCheckpointResetRemovesTheRingsPassedAfterIt`: with progress 10 and checkpoint 6, the planned run
  has `passedCount` 7, `lastCheckpointIndex` 6, and as many tick entries as rings passed. Fails today: progress is kept.
- [x] 2.4 Red: `RunResetTest.aSpawnResetKeepsNoRing`: the planned run has `passedCount` 0 and no tick entries. Fails today.
- [x] 2.5 Red: `RunResetTest.aResetRunIsGlidingWithACooldownAndNoAirborneTicks`: the planned run has `gliding` true,
  `resetCooldownTicks` 40, `airborneTicks` 0. Fails today: the fields do not exist.
- [x] 2.6 Red: `RunResetTest.aLandingBeforeTheAirborneGraceIsNotALanding`: a run with 19 airborne ticks, gliding on the
  previous tick, on the ground now, is not a landing. Fails today: `isLanded` has no grace.
- [x] 2.7 Red: `RunResetTest.aLandingDuringTheCooldownIsNotALanding`: a run with 20 airborne ticks and a cooldown of 1 is
  not a landing.
- [x] 2.8 Red: `RunResetTest.aLandingAfterTheGraceAndOutsideTheCooldownIsALanding`: 20 airborne ticks and cooldown 0 is a
  landing. Passes today; keeps the rule honest.
- [x] 2.9 Red: `RaceRunTest.airborneTicksCountConsecutiveTicksOffTheGround`: 3 airborne ticks give 3; a grounded tick gives 0.
- [x] 2.10 Red: `RaceRunTest.theResetCooldownCountsDownToZeroAndStops`: a cooldown of 2 is 1 after one tick, 0 after two, and
  stays 0.

Platform (`RacersTest`, `RunResetterTest`):

- [x] 2.11 Red: `RacersTest.placeSetsTheGlideWithoutAVelocity`: after `Racers.place`, `isFlyingWithElytra()` is true and no
  velocity other than the one passed through `VelocityExit` is sent. Fails today: place clears the flag.
- [x] 2.12 Red: `RunResetterTest.aResetAddsNoImpulse`: the only velocity sent by `reset` is `Vec3.ZERO`. Passes today; pins it.
- [x] 2.13 Red: `RunResetterTest.aResetLeavesTheRacerGliding`: after `reset`, the racer is gliding and holds the equipment.
  Fails today.

Server (`CupSessionTest`, `CupPlaythroughTest`):

- [x] 2.14 Red: `CupSessionTest.aLandingAfterAResetIsNotCountedBeforeTheGrace`: a racer reset to a checkpoint who touches the
  ground after 5 airborne ticks is not reset again. Fails today: the racer is reset again.
- [x] 2.15 Red: `CupSessionTest.aRacerBelowTheFloorDuringACooldownIsStillReset`: out-of-bounds is not gated by the cooldown.
  Passes today; pins it.
- [ ] 2.16 Red: `CupPlaythroughTest.aCheckpointResetKeepsTheScoreOfTheRingsBeforeIt`: the score after a reset to checkpoint 6
  holds the points of rings 0 to 6 only, and passing ring 7 again scores once. Fails today.
- [ ] 2.17 Red: `CupSessionGoldenMasterTest` gains one transcript, `checkpoint-reset.txt`, for a mid-course reset to a
  checkpoint. It is written by hand from the expected sequence (reset title, glide flag on the reset tick, no velocity
  other than zero) and fails until Green. It is regenerated only after Green and reviewed line by line.

## 3. Green: make the tests pass

- [x] 3.1 Green: `RunReset.plan` targets `lastCheckpointIndex` (or the spawn when it is -1), rolls the progress back to the
  target and truncates `passedOnGameTick`, sets `gliding` true and the cooldown to 40 (tasks 2.1 to 2.5).
- [x] 3.2 Green: `RaceRun` gains `airborneTicks` and `resetCooldownTicks`; `advance` takes the racer's ground state and
  maintains both counters (tasks 2.9, 2.10). Update every caller and fixture (`CupSession.raceTick`, `CupPlaythroughTest`,
  `RaceRunTest`). The secondary constructor keeps its meaning with both counters at zero.
- [x] 3.3 Green: `RunReset.isLanded` adds the grace (20) and the cooldown check on the previous run (tasks 2.6 to 2.8).
  The out-of-bounds check is unchanged and stays first in `CupSession.sendBackIfLostOrLanded`.
- [x] 3.4 Green: `Racers.place` sets `setFlyingWithElytra(true)` and keeps the facing and the equipment; update its javadoc,
  which says the reset ends the glide (task 2.11).
- [x] 3.5 Green: `RunResetter.reset` keeps its order; it passes the checkpoint number to the feedback (task 2.13).
- [x] 3.6 Green: `CupSession` passes the ground state to `advance` and the landing rule (task 2.14).
- [ ] 3.7 Green: the reset title keys name the checkpoint number or the start. Add the keys to
  `voyager/server/src/main/resources/voyager_en_US.properties` if the wording changes.
- [ ] 3.8 Green: generate `checkpoint-reset.txt` from the Green run and diff it with the hand-written expectation (task 2.17).
  Any difference is a decision, not a regeneration; stop and ask the owner.

## 4. Refactor

- [ ] 4.1 Refactor: remove the now-unused code paths (the `gliding=false` reset run, the comments that say a reset ends the
  glide), and update the javadoc of `RunReset`, `RunResetter` and `Racers` to describe the new rules. No behaviour change.
  Run `./gradlew :voyager:race:test :voyager:platform:test :voyager:server:test`.

## 5. Documentation and decision

- [ ] 5.1 `docs(race)`: rewrite `docs/reference/course-reset.md`: the target table (line ~30) names the checkpoint or the
  spawn; the definition of a landing (line ~13) and the taking-off paragraph (line ~23) name the grace and the reset glide;
  the "not implemented" list (line ~56) drops the minimum air time and the cooldown, which now exist.
- [ ] 5.2 `docs(race)`: `docs/reference/map-and-cup-files.md` notes that `CHECKPOINT` is the reset target and that a map
  with none resets to the spawn.
- [ ] 5.3 `docs(decisions)`: write `docs/decisions/0031-checkpoint-resets-with-a-deployed-elytra.md` as a MADR 4.0 record,
  status Proposed, amending ADR-0028. The owner accepts it.
- [ ] 5.4 `docs(decisions)`: add a status note to `docs/decisions/0028-racers-take-off-without-a-server-impulse.md` that
  points to ADR-0031 for the reset glide. The decision of no impulse is unchanged.
- [ ] 5.5 `docs(race)`: `docs/guides/how-to-run-a-playtest.md` gains the reset checks of task 6.2 to 6.4.
- [ ] 5.6 Check, without editing, that `docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md` (velocity section,
  about lines 411 to 416) still holds: two velocity senders, zero on map end and zero on reset.

## 6. Validate and playtest

- [ ] 6.1 Run `./gradlew build` (both trees), `./gradlew :voyager:fitness:test` (no rule added or removed; design D9), and
  `openspec validate checkpoint-resets --strict`.
- [ ] 6.2 Owner playtest A (one client): reset to a `CHECKPOINT` ring and check that the racer glides at once when the
  placement is in the air (risk R-glide-1). The committed `elytraraceblueandred` map has no `CHECKPOINT` ring, so this needs
  a playtest copy of the map with at least one checkpoint marked in it; the committed file is not edited for the test.
  Record the result.
- [ ] 6.3 Owner playtest B: reset to the spawn on the committed map, which is grounded (risk R-glide-2, option S1). Check that
  the racer takes off by jumping and deploying, as at GO. Record the result.
- [ ] 6.4 Owner playtest C: count the resets of one full run on the committed map and the landings that the grace or the
  cooldown did not count. Record both counts, and whether any chain happened (R1, R2).
- [ ] 6.5 If 6.2 fails, the fallbacks apply in order: F1 (place 2 blocks behind the target), then F2 (deferred flag). Each is
  a separate red and green step under `feat(race)`, and the owner chooses between them.
- [ ] 6.6 Lumen (`voyager-scientist`) records the playtest results in `docs/research/` (English) and the outcome in the ADR's
  Consequences section.

## 7. Archive

- [ ] 7.1 Archive commit: `docs(openspec): archive checkpoint-resets`, unless shipped with the implementation.

## 8. Pull request

- [ ] 8.1 Open the pull request against `main` with the title `feat(race): reset racers to the last checkpoint with a deployed elytra`
  (the squash-merge commit). The description lists the commits, the owner's answers to tasks 1.1 to 1.5, the playtest
  results of 6.2 to 6.4, and links ADR-0031. The PR footer has two lines: `https://claude.ai/referral/m5Ak2Sa7aQ`, then
  `https://claude.ai/code/session_01QgtyvQwXoNTXyBjSyAABrz`.
