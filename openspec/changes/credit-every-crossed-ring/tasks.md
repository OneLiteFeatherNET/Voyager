# Tasks

Commit type and scope: `fix(race)` for the code and its tests, `docs` for the `docs/` pages. Each behaviour gets a failing test
(Red) before its production code (Green). Every test follows F.I.R.S.T.: positions are fixed `Vec3` values in the test, no
sleeps, no shared static state, a fresh `ProgressTracker` input per test. Task checkboxes are ticked in the follow-up pull request,
never in the code pull request.

## 1. Red: ring credit

- [ ] 1.1 Red: `ProgressTrackerTest` "a step that crosses two rings credits both, in order" (`fix(race)`; replaces
  `countsAtMostOneRingPerTick` once the owner confirms the rule)
- [ ] 1.2 Red: `ProgressTrackerTest` "a step that crosses the first ring and not the second credits only the first" (`fix(race)`)
- [ ] 1.3 Red: `ProgressTrackerTest` "a ring crossed earlier on the segment than the next ring is credited first" (`fix(race)`)
- [ ] 1.4 Red: `RingPassTest` "crossingParameter returns the point along the segment where the plane is crossed inside the area"
  (`fix(race)`)
- [ ] 1.5 Red: run 1.1 to 1.4 and confirm each fails for the stated reason; record the failure messages in the code pull request

## 2. Green: ring credit

- [ ] 2.1 Green: add `RingPass.crossingParameter`, and make `RingPass.crosses` delegate to it (`fix(race)`)
- [ ] 2.2 Green: credit following rings on the same segment in parameter order in `ProgressTracker.advance` (`fix(race)`)
- [ ] 2.3 Green: make 1.1 to 1.4 pass; `RingPassTest` and `ProgressTrackerTest` stay green (`fix(race)`)

## 3. Red and Green: reset forgets the last position

- [ ] 3.1 Red: `CupSessionResetTest` "the first tick after a reset credits no ring and applies no ring effect" (`fix(race)`);
  the test drives two explicit ticks with a fake `RunResetter` and a fixed racer position
- [ ] 3.2 Red: `CupSessionResetTest` "the tick after the first reset tick is measured from the reset position" (`fix(race)`)
- [ ] 3.3 Green: clear `lastPositions` and `lastVelocities` for the racer in the reset path of `CupSession` (`fix(race)`)

## 4. Documentation

- [ ] 4.1 Update the ring-progress reference in `docs/reference/` to say that every ring crossed on one tick counts (`docs`,
  separate commit)

## 5. Verify

- [ ] 5.1 Run `./gradlew :voyager:race:test :voyager:platform:test` through `heavy` and confirm both pass
- [ ] 5.2 Run `./gradlew build` (both trees) through `heavy` before marking the change done

## 6. Open the code pull request

- [ ] 6.1 Open the code pull request titled `fix(race): credit every ring crossed on a tick and forget the position after a reset`,
  branched from the remote main branch that contains this proposal
