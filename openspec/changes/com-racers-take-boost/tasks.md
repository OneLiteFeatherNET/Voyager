# Tasks

Commit type and scope: `fix(race)` for the code and its tests, `docs` for the `docs/` pages. Each behaviour gets a failing test
(Red) before its production code (Green). Every test follows F.I.R.S.T.: the COM brain is driven one tick at a time with explicit
tick numbers, the effect is a recording fake, no sleeps, no shared static state, a fresh brain per test. Task checkboxes are ticked
in the follow-up pull request, never in the code pull request.

## 1. Red: the effect and the brain

- [ ] 1.1 Red: `ComSpeedEffectTest` "multiplying scales the target speed by the factor" (`fix(race)`)
- [ ] 1.2 Red: `PilotBrainTest` "the target speed is unchanged before the reaction delay ends" (`fix(race)`)
- [ ] 1.3 Red: `PilotBrainTest` "the target speed is multiplied by the effect on the tick the delay ends" (`fix(race)`)
- [ ] 1.4 Red: `PilotBrainTest` "a HARD racer at the cap is not lifted above the cap by a boost" (`fix(race)`)
- [ ] 1.5 Red: run 1.1 to 1.4 and confirm each fails for the stated reason; record the failure messages in the code pull request

## 2. Green: the effect and the brain

- [ ] 2.1 Green: add `ComSpeedEffect.multiplying` (`fix(race)`)
- [ ] 2.2 Green: add the boost parameter to `ComBrain.step` and pass it to the five-argument `ComPilot.step` in `PilotBrain` (`fix(race)`)
- [ ] 2.3 Green: make 1.1 to 1.4 pass (`fix(race)`)

## 3. Red and Green: the bridge in ComField

- [ ] 3.1 Red: `ComFieldBoostTest` "a COM racer on a map with boost factor 1.2 receives an effect that multiplies by 1.2" (`fix(race)`);
  uses the package-private `ComField` constructor with a recording fake brain
- [ ] 3.2 Red: `ComFieldBoostTest` "two maps with different boost factors give their COM racers different effects" (`fix(race)`)
- [ ] 3.3 Red: `ComFieldBoostTest` "a map that states no ringEffects gives the default factor 1.5" (`fix(race)`)
- [ ] 3.4 Green: build the effect from `map.ringEffects().boostMultiplier()` and store it with the racer (`fix(race)`)
- [ ] 3.5 Green: make 3.1 to 3.3 pass (`fix(race)`)

## 4. Documentation

- [ ] 4.1 Update the COM reference in `docs/reference/` to say that computer racers take the map's boost after their reaction
  delay, within the reference cap (`docs`, separate commit)

## 5. Verify

- [ ] 5.1 Run `./gradlew :voyager:race:test :voyager:platform:test :voyager:fitness:test` through `heavy` and confirm all pass
- [ ] 5.2 Record the COM lap times on the committed cup maps before and after in the code pull request, for the owner's balance review
- [ ] 5.3 Run `./gradlew build` (both trees) through `heavy` before marking the change done

## 6. Open the code pull request

- [ ] 6.1 Open the code pull request titled `fix(race): let computer racers take the map's boost after their reaction delay`,
  branched from the remote main branch that contains this proposal
