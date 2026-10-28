# Tasks

Commit type and scope: `fix(race)` for the code and its tests, `docs` for `docs/` pages and the decision record. Each
behaviour gets a failing test (Red) before its production code (Green). Every test follows F.I.R.S.T.: no sleeps, time is
injected through `java.time.Clock` or the race clock ticked explicitly, a fresh fixture per test, no shared static state.
Task checkboxes are ticked in the follow-up pull request, never in the code pull request.

## 1. Red: the three edges

- [ ] 1.1 Red: `RacingLineStretchTest` "the stretch starts at the last ring passed, so the segment being flown stays drawn"
  (`fix(race)`)
- [ ] 1.2 Red: `RacingLineStretchTest` "a window stretch starts at the last ring passed and reaches lookAheadRings past the
  ring being headed for" (`fix(race)`)
- [ ] 1.3 Red: `GlideOutTest` "speed at the end of 50 ticks is at most a quarter of the finish speed and never reverses"
  (`fix(race)`)
- [ ] 1.4 Red: `CupSessionGlideOutTest` "a finisher keeps gliding for 50 ticks after the game phase ends, then stands down at
  the spawn of the map just finished" (`fix(race)`)
- [ ] 1.5 Red: `CupSessionGlideOutTest` "a racer still airborne when the time limit ends is stood down on that tick"
  (`fix(race)`)
- [ ] 1.6 Red: `StartFreezeTest` "a racer placed on the second map of a cup is held at its spawn, facing its first ring, until
  GO" (`fix(race)`)
- [ ] 1.7 Red: `StartFreezeTest` "a racer who moves more than 0.1 block during the count is returned to the spawn with zero
  velocity" (`fix(race)`)
- [ ] 1.8 Red: `StartFreezeTest` "a racer who deploys the elytra before GO is turned not-gliding on the same tick"
  (`fix(race)`)
- [ ] 1.9 Red: `StartFreezeTest` "a racer is not placed until the preload around the spawn has completed" (`fix(race)`)
- [ ] 1.10 Red: run 1.1 to 1.9 and confirm each fails for the stated reason; record the failure messages in the code pull
  request (`fix(race)`)

## 2. Green: the three edges

- [ ] 2.1 Green: `GuideLineRenderer.stretch` starts at `max(passedCount - 1, 0)` in both modes; replace the comment at
  `CupSession.java:739-742` with the rule that the segment being flown is shown (`fix(race)`)
- [ ] 2.2 Green: `race/glide` with `GlideOut` (length 50 ticks, damping to a quarter), `package-info` with `@NotNullByDefault`,
  no Minestom import (`fix(race)`)
- [ ] 2.3 Green: `CupSession` ticks each finisher's glide-out in GAME and END, applies the damped velocity through
  `VelocityExit`, and defers `Racers.standDown` for a finisher to the end of their glide-out; `mapFinished` keeps the stand-down
  for racers still airborne at the time limit (`fix(race)`)
- [ ] 2.4 Green: `RaceFeedback` plays the finish firework and finish sound once at the finish ring; the sound is a sample that
  is not the countdown beat, the ring bell or the lobby chime (`fix(race)`)
- [ ] 2.5 Green: `Racers` pins a placed racer to the spawn until GO, turns gliding off each tick before GO, and leaves the
  GO release unchanged (no impulse, #311) (`fix(race)`)
- [ ] 2.6 Green: `CupSession.enterMap` holds the racers in place while `Arrivals` preloads, and places them only after the
  preload (`fix(race)`)
- [ ] 2.7 Refactor: confirm 1.1 to 1.9 pass, and that the existing `RacingLine`, `GuideLineRenderer`, `Racers` and
  `waiting-lobby` tests are unchanged and still pass (`fix(race)`)

## 3. Docs and decision record

- [ ] 3.1 Update `docs/reference/hud.md` (the finish and the start freeze) and `docs/reference/message-style-guide.md` (the
  finish firework row in the sound table) (`docs`)
- [ ] 3.2 Write the MADR decision that the glide-out is a second kind of server-sent velocity, under `docs/decisions/` with
  status Proposed, numbered after the decisions in flight (ADR-0040 is held by `add-catalogue-keys-and-authors`) (`docs`)
- [ ] 3.3 Record the view distance as a deployment setting (`-Dminestom.chunk-view-distance=16`,
  `-Dminestom.entity-view-distance=10`) in the server deployment reference under `docs/`, with its effect on the preload
  (`docs`)

## 4. Checks

- [ ] 4.1 Run `./gradlew :voyager:race:test :voyager:platform:test :voyager:fitness:test` (`fix(race)`)
- [ ] 4.2 Run `./gradlew build` for both trees (`fix(race)`)
- [ ] 4.3 Smoke-boot the server shadow JAR with LuckPerms as the apply rules require, and quote the start lines in the code
  pull request (`fix(race)`)
- [ ] 4.4 Playtest on the playtest server at view distance 16 and entity distance 10: measure the time from arrival to the
  placement of the racer on palmbeach, dragonlobby and memoria, and record whether the freeze holds and whether the count
  stalls; report the numbers in the code pull request (`fix(race)`)

## 5. Ship

- [ ] 5.1 Open the code pull request under the title `fix(race): glide out after the finish and hold racers at spawn until GO`.
  Write the description as the racer sees a map end: the flight that eases out after the finish, and the wait at the spawn
  until GO. Include the Red messages (1.10), the smoke lines (4.3) and the preload numbers (4.4). Do not add a session link
  (`fix(race)`)
