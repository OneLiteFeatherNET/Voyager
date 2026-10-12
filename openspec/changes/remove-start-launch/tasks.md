# Tasks

Conventional Commits: code commits are `fix(race): ...`; docs and ADR commits are `docs(race): ...` or `docs(openspec): ...`.
Each commit carries one type. Red before Green for every behaviour (Red/Green TDD). Every test follows F.I.R.S.T.:
an `Env` with explicit ticks, no sleeps, no wall-clock reads.

## 1. Decision

- [x] 1.1 Owner answers recorded in `design.md` ("Decisions recorded from the owner"): clock starts at GO, reset zeroes
  velocity, mid-air spawns accepted, GO jump hint added (D10).

## 2. Red: behaviour tests (`voyager/platform`, `RacersTest` and `CupSession`/`RunResetter` tests)

- [ ] 2.1 Red: `RacersTest` case `goGivesNoImpulseAndDoesNotGlide`: after `Racers.faceCourse` and the GO path, the
  racer's velocity is zero and `isFlyingWithElytra()` is false. Fails today because `mapStarted` calls `launch`.
- [ ] 2.2 Red: `RacersTest` case `goKeepsTheElytraAndTheRockets`: the chest slot holds an elytra and slot 0 holds rockets
  after the GO path.
- [ ] 2.3 Red: `RunResetterTest` case `aResetPlacesTheRacerWithoutAnImpulse`: after `reset`, the racer is at the target
  centre, holds the equipment, is not gliding and has zero velocity. Fails today because `reset` calls `launch`.
- [ ] 2.4 Red: `RunResetterTest` case `aResetRacerIsNotALanding`: the next race tick after a reset, with the racer not
  gliding, does not reset the racer again.
- [ ] 2.6 Red: `RacersTest` case `goShowsTheJumpAndDeployTitle`: the GO feedback sends the GO title and the subtitle
  `voyager.race.go.jump` in the same tick. Fails today because the GO title has no subtitle.
- [ ] 2.7 Red: `RunResetterTest` case `aResetZeroesTheRacersVelocity`: after `reset`, the velocity sent is `Vec3.ZERO`
  (through `VelocityExit`) and no other.
- [ ] 2.5 Red: `RacersTest` case `deployingTheElytraAfterGoStartsTheGlide` (flight is the client's): a `setFlyingWithElytra(true)`
  from the client is observed by the race tick, and the racer's run advances.

## 3. Green: remove the launch

- [ ] 3.1 Green: `CupSession.mapStarted` drops the `Racers.launch(racer, map)` call; keeps `transition.advanceTo` and
  `RaceFeedback.go`.
- [ ] 3.2 Green: add `Racers.place(Player, Vec3 from, Vec3 toward)`: equips the racer, turns them toward `toward`, does
  not set gliding and does not send velocity. Use it from `RunResetter.reset` in place of `launch`. The reset sends
  `Vec3.ZERO` through `VelocityExit` (task 3.8).
- [ ] 3.7 Green: `RaceFeedback.go` sends the subtitle `Messages.goJump()` (translatable `voyager.race.go.jump`, "Jump, then
  deploy your elytra.") in the same tick as the GO title; add the key to `voyager/server/src/main/resources/voyager_en_US.properties`.
- [ ] 3.8 Green: `RunResetter.reset` sends `Vec3.ZERO` through `VelocityExit` (decided in design D6), so the reset racer's velocity is zero.
- [ ] 3.3 Green: remove `Racers.launch(Player, MapDefinition)`, `Racers.launch(Player, Vec3, Vec3)`, `launchVelocity`,
  `LAUNCH_FORWARD_PER_TICK` and `LAUNCH_UP_PER_TICK`, and the RacersTest cases that pin the impulse (lines ~75-101).
- [ ] 3.4 Green: update the `Racers` class javadoc (the "launch numbers are seeds" section and the `VelocityExit`
  paragraph) and the `VelocityExit` javadoc to name the two remaining sources (ring BOOST/SLOW, zeroing).
- [ ] 3.5 Green: `ApiPurityTest.onlyVelocityExitSendsAVelocityToMinestom` keeps the rule; its `because` text names the
  two sources. Run `./gradlew :voyager:fitness:test` and `./gradlew :voyager:platform:test`.
- [ ] 3.6 Confirm the golden transcripts do not log velocity (they are unaffected); if any transcript asserts a glide at
  the GO tick, update it under this commit.

## 4. Refactor

- [ ] 4.1 Refactor: remove any now-unused imports and fields; no behaviour change. Re-run both test tasks.

## 5. Documentation and decision

- [ ] 5.1 `docs(race)`: rewrite `docs/reference/course-reset.md` lines 23 and 32 (the relaunch is not a landing; the
  racer takes off by themselves and is not launched).
- [ ] 5.2 `docs(race)`: correct the velocity section of `docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`
  (about lines 411-416): two sends remain, the firework is an entity, and the take-off is the client's.
- [ ] 5.3 `docs(race)`: note in `docs/guides/how-to-run-a-playtest.md` that a racer jumps and deploys the elytra on GO,
  and fires a rocket only after the elytra is deployed.
- [ ] 5.4 `docs(decisions)`: write `docs/decisions/0028-racers-take-off-without-a-server-impulse.md` as a MADR with
  status Proposed (design D9). The owner accepts it; the agent does not mark it Accepted.

## 6. Validate and playtest

- [ ] 6.1 Run `./gradlew build` (both trees) and `openspec validate remove-start-launch --strict`.
- [ ] 6.2 Owner playtest with one client on the committed `elytraraceblueandred` map: jump, deploy, fire a rocket after GO;
  record the time from GO to first glide and whether the spawn is grounded. Record the result in `docs/research/`
  (Lumen) and in the ADR's Consequences.
- [ ] 6.3 Owner playtest of a reset: fall out of bounds and check that the racer arrives at the ring centre with the
  elytra in hand and takes off by themselves.

## 7. Archive

- [ ] 7.1 Archive commit: `docs(openspec): archive remove-start-launch`, unless shipped with the implementation.

## 8. Pull request

- [ ] 8.1 Open the pull request against `main` with the title `fix(race): let racers take off themselves at GO and after a reset`
  (the squash-merge commit). The description lists the commits, the open questions from design.md, and links the playtest
  result of 6.2 and 6.3.
