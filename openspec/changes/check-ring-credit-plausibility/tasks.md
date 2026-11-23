# Tasks

Commit types: `feat(race)` for the bound, the flag and the wiring, `docs` for `docs/`. Each behaviour gets a failing test (Red)
before its production code (Green). Every test follows F.I.R.S.T.: no sleep, no system clock, a fresh fixture per test, no shared
static state. The phase 1 pull request is the first code change; phase 2 is a second pull request under the same type and
scope, merged after the owner accepts the thresholds. The last task of each pull request opens it.

## 1. Red: the displacement bound

- [ ] 1.1 Red: `DisplacementBoundTest` "a displacement no larger than the bound is plausible with zero excess" (`feat(race)`)
- [ ] 1.2 Red: `DisplacementBoundTest` "a displacement larger than the bound is implausible and the excess is the difference" (`feat(race)`)
- [ ] 1.3 Red: `DisplacementBoundTest` "a zero-speed racer standing still is plausible for any zero displacement" (`feat(race)`)
- [ ] 1.4 Red: run 1.1 to 1.3 and confirm each fails for the stated reason; record the messages in the pull request description (`feat(race)`)

## 2. Green: the bound

- [ ] 2.1 Green: `DisplacementBound` and `DisplacementVerdict` in `voyager-physics` `physics.plausibility`, named constants,
  private constructor, no Minestom import (`feat(race)`)

## 3. Red: the race's use of the flag

- [ ] 3.1 Red: `ProgressTrackerPlausibilityTest` "observe mode credits a ring on a flagged tick and keeps the largest excess" (`feat(race)`)
- [ ] 3.2 Red: `ProgressTrackerPlausibilityTest` "a clean run has no plausibility error" (`feat(race)`)
- [ ] 3.3 Red: `ProgressTrackerPlausibilityTest` "the check off grants exactly what it granted before this change" (`feat(race)`)
- [ ] 3.4 Red: run 3.1 to 3.3 and confirm each fails for the stated reason (`feat(race)`)

## 4. Green: the flag in race

- [ ] 4.1 Green: `RaceRun` advance takes `plausible`; `ProgressTracker` records the flag and the largest excess and does not
  change credit in observe mode (`feat(race)`)

## 5. Red then Green: the platform wiring and the setting

- [ ] 5.1 Red: `RingPlausibilitySettingsTest` "unset is observe; `off`, `observe` and `enforce` are read; any other value is one
  error that names the key" (`feat(race)`)
- [ ] 5.2 Green: `RingPlausibilitySettings` in `voyager-server` `server.config`, `VOYAGER_RING_PLAUSIBILITY` added to the keys and
  to `ConfigCheck` (`feat(race)`)
- [ ] 5.3 Red: `CupSessionPlausibilityTest` "the sampling loop passes the verdict of the bound for each racer's tick" (`feat(race)`)
- [ ] 5.4 Green: the sampling loop in `platform.cup` computes the verdict from the shadow speed and passes it to race (`feat(race)`)

## 6. Red then Green: persistence

- [ ] 6.1 Red: `RaceResultWriteTest` "a flagged run stores its largest excess, a clean run stores null" (`feat(race)`)
- [ ] 6.2 Green: the completion record carries the excess into `plausibility_error` (`feat(race)`)

## 7. Red then Green: fixtures and fitness

- [ ] 7.1 Red: `LegitimateTraceTest` "no tick of the committed legitimate traces is flagged" (`feat(race)`)
- [ ] 7.2 Green: commit the legitimate trace fixtures and set the constants that pass them (`feat(race)`)
- [ ] 7.3 Red then Green: two `@ArchTest` rules in `voyager-fitness` (race does not depend on physics; the bound has no Minestom,
  I/O or NIO import), each with `allowEmptyShould(false)` (`feat(race)`)

## 8. Docs

- [ ] 8.1 `docs/reference/configuration.md` names `VOYAGER_RING_PLAUSIBILITY` with its three values and default (`docs`)
- [ ] 8.2 `docs/reference/elytra-physics-26.2.md` and `docs/elytra-physics-reference.md` state what is checked and what is not,
  and replace the claim at `docs/elytra-physics-reference.md:473` with the observe-mode behaviour (`docs`)
- [ ] 8.3 `docs/decisions/0049-ring-credit-plausibility.md` (MADR 4.0, Proposed) records D1 to D3 and the threshold source (`docs`)
- [ ] 8.4 `docs/guides/how-to-record-a-trace.md` names the plausibility flag and how to read it from `plausibility_error` (`docs`)

## 9. Open the phase 1 code pull request

- [ ] 9.1 Open the phase 1 pull request under `feat(race): observe ring credit plausibility`. It contains no file under
  `openspec/`, and no task is ticked in it.

## 10. Calibration and phase 2 (owner-gated)

- [ ] 10.1 Record traces on a real client at normal and high latency and commit them as fixtures with the justifying note (`docs`)
- [ ] 10.2 Owner accepts the thresholds and the enforcement policy from the open questions (`docs`)
- [ ] 10.3 Red: `ProgressTrackerPlausibilityTest` "enforce mode withholds credit for a flagged segment and never grants the
  finish from one" (`feat(race)`)
- [ ] 10.4 Green: enforce mode withholds credit and sends the one action-bar line (`feat(race)`)
- [ ] 10.5 Open the phase 2 pull request under `feat(race): withhold ring credit for implausible movement`. It contains no file
  under `openspec/`, and no task is ticked in it.
