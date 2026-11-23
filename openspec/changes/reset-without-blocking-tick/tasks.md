# Tasks

Commit type and scope: `perf(race)` for the code and its tests, `docs` for the `docs/` pages. Each behaviour gets a failing test
(Red) before its production code (Green). Every test follows F.I.R.S.T.: the preload is driven by a fake chunk loader whose
futures the test completes by hand, the tick is driven explicitly, no sleeps, a fresh fixture per test. Task checkboxes are ticked
in the follow-up pull request, never in the code pull request.

## 1. Measure first

- [ ] 1.1 Measure the stall of a reset on the tick with the deployment's view distance: time the `reposition` call on a test
  world with the target chunks unloaded, and record the number in the code pull request (`perf(race)`)

## 2. Red: pending reset

- [ ] 2.1 Red: `PendingResetPlacementTest` "a reset returns on the same tick and waits for no chunk" (`perf(race)`)
- [ ] 2.2 Red: `PendingResetPlacementTest` "the reset is applied on the tick after the last target chunk is present" (`perf(race)`)
- [ ] 2.3 Red: `PendingResetPlacementTest` "the racer is held and is not credited with a ring while a chunk is pending" (`perf(race)`)
- [ ] 2.4 Red: `PendingResetPlacementTest` "a second reset during the hold replaces the pending target and keeps the hold" (`perf(race)`)
- [ ] 2.5 Red: `PendingResetPlacementTest` "a failed chunk holds the racer, logs once with the world name, and does not throw into the
  tick" (`perf(race)`)
- [ ] 2.6 Red: run 2.1 to 2.5 and confirm each fails for the stated reason; record the failure messages in the code pull request

## 3. Green: pending reset

- [ ] 3.1 Green: add `PendingResetPlacement` and route `RunResetter.reset` through it (`perf(race)`)
- [ ] 3.2 Green: make 2.1 to 2.5 pass (`perf(race)`)
- [ ] 3.3 Green: skip the advance and ring progress for held racers in `CupSession` (`perf(race)`)

## 4. Red and Green: baseline

- [ ] 4.1 Red: `TickBoundarySeededTest` "the frozen baseline has no entry for the reset path" (`perf(race)`)
- [ ] 4.2 Green: remove the `Arrivals` entry from the committed baseline and remove the reset call to `Arrivals.reposition`
  (`perf(race)`)

## 5. Documentation

- [ ] 5.1 Update the reset reference in `docs/reference/` to say that a reset holds the racer for the few ticks the chunks take
  (`docs`, separate commit)

## 6. Verify

- [ ] 6.1 Run `./gradlew :voyager:race:test :voyager:platform:test :voyager:fitness:test` through `heavy` and confirm all pass
- [ ] 6.2 Measure the stall again with the same method as 1.1 and record the number in the code pull request
- [ ] 6.3 Run `./gradlew build` (both trees) through `heavy` before marking the change done

## 7. Open the code pull request

- [ ] 7.1 Open the code pull request titled `perf(race): apply a reset after its chunks load instead of on the tick`, branched
  from the remote main branch that contains this proposal
