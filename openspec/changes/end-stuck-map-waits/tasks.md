# Tasks

Commit types: `feat(race)` for the grace window, retire, the leave rule, the joiner estimate and their tests; `docs` for `docs/`.
Each behaviour gets a failing test (Red) before its production code (Green). Every test follows F.I.R.S.T.: no sleep, no wall clock
(the window and the estimate count ticks), a fresh fixture per test, no shared static state. The code pull request contains no file
under `openspec/`, and no task is ticked in it. Do not start the code until the owner has answered the open questions.

## 1. Red: the grace window

- [ ] 1.1 Red: `GraceWindowTest` "the window opens at the first finish with the floor when the reference time is short" (`feat(race)`)
- [ ] 1.2 Red: `GraceWindowTest` "the window is 1.5 times the reference time when that is longer than the floor" (`feat(race)`)
- [ ] 1.3 Red: `GraceWindowTest` "the window never extends the race cap" (`feat(race)`)
- [ ] 1.4 Red: `GraceWindowTest` "no window opens before the first finish" (`feat(race)`)

## 2. Green: the grace window

- [ ] 2.1 Green: `GraceWindow` record in `voyager/race` (`race.flow`), a pure tick counter with no Minestom import (`feat(race)`)

## 3. Red: the end of a map with a grace window

- [ ] 3.1 Red: `MapEndWithGraceTest` "a racer still racing at the window's end is scored DNF with ring points and no medal" (`feat(race)`)
- [ ] 3.2 Red: `MapEndWithGraceTest` "everyone finishing before the window ends ends the map at the last finish, with no DNF" (`feat(race)`)
- [ ] 3.3 Red: `MapEndWithGraceTest` "with no finish the map ends at the race cap as before" (`feat(race)`)

## 4. Green: the end of a map

- [ ] 4.1 Green: `RaceStateMachine` ends the `GAME` phase when the grace window ends, and `MapScorer` scores the still-racing racers
  through the existing `MedalTier.DNF` path (`feat(race)`)

## 5. Red: retire

- [ ] 5.1 Red: `RetirementTest` "a retired run is scored DNF with its ring points and no medal points" (`feat(race)`)
- [ ] 5.2 Red: `RetirementTest` "a finished racer's retire changes nothing" (`feat(race)`)
- [ ] 5.3 Red: `RetireActionTest` "`/retire` from the lobby is refused with a translated line" (`feat(race)`)
- [ ] 5.4 Red: `RetireActionTest` "the retire item of a racing racer scores the run and leaves the racer in the cup" (`feat(race)`)

## 6. Green: retire

- [ ] 6.1 Green: `Retirement` in `race.scoring` and `RetireAction` in `platform.cup`, with the `/retire` command and the retire item
  (`feat(race)`)

## 7. Red: the leave rule

- [ ] 7.1 Red: `LeaveRuleTest` "the leave item and `/hub` are refused while a racer is racing and unfinished" (`feat(race)`)
- [ ] 7.2 Red: `LeaveRuleTest` "the leave item and `/hub` are allowed between maps and after a finish" (`feat(race)`)
- [ ] 7.3 Red: `LeaveRuleTest` "a racer who leaves between maps is not counted for the next map" (`feat(race)`)
- [ ] 7.4 Red: `LobbyItemsTest` "the leave item returns in the between-maps wait and is removed again at the next GO" (`feat(race)`)

## 8. Green: the leave rule

- [ ] 8.1 Green: `LeaveRule` in `platform.lobby` used by the leave item, `/leave` and `/hub`; `WaitingRoom` returns the leave item in the
  wait and removes it at GO (`feat(race)`)

## 9. Red: the joiner's estimate

- [ ] 9.1 Red: `JoinEstimateTest` "a joiner during a map is told the upper bound, rounded up to whole minutes" (`feat(race)`)
- [ ] 9.2 Red: `JoinEstimateTest` "a joiner during the wait after the last map is told the lobby length as the bound" (`feat(race)`)
- [ ] 9.3 Red: `JoinEstimateTest` "the estimate counts ticks and reads no clock" (`feat(race)`)

## 10. Green: the joiner's estimate

- [ ] 10.1 Green: `JoinEstimate` record in `voyager/race` (`race.flow`) and the joined line in `WaitingRoom` reads it (`feat(race)`)

## 11. Red: the grace label on the boss bar

- [ ] 11.1 Red: `GraceBossBarTest` "a racer still racing sees the grace label after the first finish, and none before" (`feat(race)`)
- [ ] 11.2 Red: `MessageBundleParityTest` fails until the grace label exists in English and German (`feat(race)`)

## 12. Green: the grace label

- [ ] 12.1 Green: the boss bar label and its keys in `voyager_en_US.properties` and the German bundle (`feat(race)`)

## 13. Replay and verify

- [ ] 13.1 Replay the cup-session golden transcripts; every map in which everyone finishes must be byte-identical (`feat(race)`)
- [ ] 13.2 Run `:voyager:race:test` and `:voyager:fitness:test` through `heavy` with `--max-workers=2` (`feat(race)`)

## 14. Docs

- [ ] 14.1 Update the racer-facing page in `docs/` for `/retire`, the grace window and leaving between maps (`docs`)
- [ ] 14.2 If the owner accepts overturning the per-map Won't, write a MADR file in `docs/` for the grace rule, and link it from the
  reference page (`docs`)

## 15. Open the code pull request

- [ ] 15.1 Open the code pull request with the title `feat(race): end a map after a grace window and let racers retire`, from a
  branch based on the remote main branch that contains the merged proposal, with no file under `openspec/` (`feat(race)`)
