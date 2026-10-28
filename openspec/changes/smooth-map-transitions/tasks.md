# Tasks

Commit type and scope: `fix(race)` for the code and its tests, `docs` for the `docs/` pages and the decision record. Each
behaviour gets a failing test (Red) before its production code (Green). Every test follows F.I.R.S.T.: no sleeps, time comes
from an injected clock or from an explicitly ticked race clock, the preload is driven by a fake `ChunkLoader` whose futures the
test completes by hand, a fresh fixture per test, no shared static state. Task checkboxes are ticked in the follow-up pull
request, never in the code pull request.

## 1. Red: the three edges and the preload

- [ ] 1.1 Red: `RacingLineStretchTest` "the stretch starts at the last ring passed, so the segment being flown stays drawn"
  (`fix(race)`)
- [ ] 1.2 Red: `RacingLineStretchTest` "a window stretch starts at the last ring passed and reaches lookAheadRings past the
  ring being headed for" (`fix(race)`)
- [ ] 1.3 Red: `GlideOutTest` "speed at the end of 50 ticks is at most a quarter of the finish speed and never reverses"
  (`fix(race)`)
- [ ] 1.4 Red: `GlideOutSessionTest` "a finisher keeps gliding for 50 ticks, then is held in place with the elytra off until
  placed" (`fix(race)`)
- [ ] 1.5 Red: `GlideOutSessionTest` "a racer airborne when the time limit ends gets the same glide-out, and a racer on the ground
  gets none" (`fix(race)`; replaces the earlier "stood down at once" expectation)
- [ ] 1.6 Red: `WaitArrivalTest` "an early finisher is placed when their own glide-out ends, and the racers still racing are not
  moved" (`fix(race)`)
- [ ] 1.7 Red: `WaitArrivalTest` "the first map's wait starts at its first lobby tick and places every racer on the cup"
  (`fix(race)`)
- [ ] 1.8 Red: `WaitArrivalTest` "a placed racer's run on the previous map is not advanced, its HUD and guide line are not sent,
  and the run is still scored at the phase end" (`fix(race)`)
- [ ] 1.9 Red: `WaitArrivalTest` "GO does not move a racer who is already placed" (`fix(race)`)
- [ ] 1.10 Red: `WaitArrivalTest` "a racer who joins during a wait is placed when their preload completes" (`fix(race)`)
- [ ] 1.11 Red: `AsyncPreloadTest` "starting a preload returns on the same tick and waits for no chunk" (`fix(race)`)
- [ ] 1.12 Red: `AsyncPreloadTest` "the racer is not placed while one chunk is pending, and is placed on the tick after the last
  one completes" (`fix(race)`)
- [ ] 1.13 Red: `AsyncPreloadTest` "a failed chunk holds the racer in place, logs the failure once with the world name, and throws
  nothing into the tick" (`fix(race)`)
- [ ] 1.14 Red: `AsyncPreloadTest` "one racer's pending chunks do not delay another racer whose chunks are present" (`fix(race)`)
- [ ] 1.15 Red: `StartFreezeTest` "a placed racer is held at the spawn facing ring 1 until GO, on the second map too" (`fix(race)`)
- [ ] 1.16 Red: `StartFreezeTest` "a racer who moves more than 0.1 block before GO is returned to the spawn with zero velocity"
  (`fix(race)`)
- [ ] 1.17 Red: `StartFreezeTest` "a racer who deploys the elytra before GO is turned not-gliding on the same tick" (`fix(race)`)
- [ ] 1.18 Red: run 1.1 to 1.17 and confirm each fails for the stated reason; record the failure messages in the code pull
  request (`fix(race)`)

## 2. Green: the three edges and the preload

- [ ] 2.1 Green: `GuideLineRenderer.stretch` starts at `max(passedCount - 1, 0)` in both modes; replace the comment at
  `CupSession.java:739-742` with the rule that the segment being flown is shown (`fix(race)`)
- [ ] 2.2 Green: `race/glide` with `GlideOut` (50 ticks, damping to a quarter), `package-info` with `@NotNullByDefault`, no
  Minestom import (`fix(race)`)
- [ ] 2.3 Green: `ChunkLoader` (functional interface, production passes `Instance::loadOptionalChunk`) and `PendingArrival` in
  `platform/world`; the tick check ticks each pending arrival once and never joins (`fix(race)`)
- [ ] 2.4 Green: `Arrivals.arrive` no longer joins the preload or the teleport on the tick; `Arrivals.reposition` keeps its join
  and is untouched (`fix(race)`)
- [ ] 2.5 Green: split `MapTransition.advanceTo`: cup-wide state and `runs.clearAll` move to the phase end; per-racer placement
  calls `PendingArrival` (`fix(race)`)
- [ ] 2.6 Green: `CupSession` marks a placed racer as having left the previous map; `raceTick`, the HUD and the guide line skip
  them; `mapFinished` still scores their run (`fix(race)`)
- [ ] 2.7 Green: `CupSession` starts each finisher's glide-out on the finish tick, holds the racer after it, and gives the same
  glide-out to racers still gliding at the time limit; the glide-out keeps the `MapDefinition` it started on (`fix(race)`)
- [ ] 2.8 Green: the finish firework and sound in `RaceFeedback`, once at the finish ring (`fix(race)`)
- [ ] 2.9 Green: `Racers` pins a placed racer to the spawn until GO, turns gliding off each tick before GO, and leaves the GO
  release unchanged (`fix(race)`)
- [ ] 2.10 Green: `mapStarted` places only the racers not yet placed on the map (`fix(race)`)
- [ ] 2.11 Refactor: confirm 1.1 to 1.17 pass, and that the existing `RacingLine`, `GuideLineRenderer`, `Racers`, `MapTransition`
  and `waiting-lobby` tests are unchanged and still pass, or are listed in the code pull request with the reason (`fix(race)`)

## 3. Docs and decision record

- [ ] 3.1 Update `docs/reference/hud.md` (the finish, the wait and the freeze) and `docs/reference/message-style-guide.md` (the
  finish firework row in the sound table) (`docs`)
- [ ] 3.2 Write the MADR decision that braking after the finish and after the time limit is a second kind of server-sent
  velocity, under `docs/decisions/` with status Proposed, numbered after the decisions in flight (ADR-0040 is held by
  `add-catalogue-keys-and-authors`) (`docs`)
- [ ] 3.3 Record the view distance as a deployment setting (`-Dminestom.chunk-view-distance=16`,
  `-Dminestom.entity-view-distance=10`) in the server deployment reference under `docs/`, with its effect on the preload (`docs`)

## 4. Checks

- [ ] 4.1 Run `./gradlew :voyager:race:test :voyager:platform:test :voyager:fitness:test` (`fix(race)`)
- [ ] 4.2 Run `./gradlew build` for both trees (`fix(race)`)
- [ ] 4.3 Smoke-boot the server shadow JAR with LuckPerms as the apply rules require, and quote the start lines in the code
  pull request (`fix(race)`)
- [ ] 4.4 Playtest on the playtest server at view distance 16 and entity distance 10: record the longest server tick during a
  preload, the time from placement to GO on palmbeach, dragonlobby and memoria, and whether the freeze held (`fix(race)`)

## 5. Ship

- [ ] 5.1 Open the code pull request under the title `fix(race): glide out after the finish and hold racers at spawn until GO`.
  Write the description as the racer sees a map end: the flight that eases out after the finish, the wait at the next map's
  spawn, and the line that stays until the next ring. Include the Red messages (1.18), the smoke lines (4.3) and the tick and
  placement numbers (4.4). Do not add a session link (`fix(race)`)
