# Tasks

Commit types: `feat(race)` for the start gate, the solo wait, the grid, the labels, the shipped cup and their tests; `docs` for
`docs/`. Each behaviour gets a failing test (Red) before its production code (Green). Every test follows F.I.R.S.T.: no sleep, no
clock (the solo wait counts ticks, the drive is explicit), a fresh fixture per test, no shared static state. The code pull request
contains no file under `openspec/`, and no task is ticked in it.

## 1. Red: the mode minimum and the override

- [ ] 1.1 Red: `StartGateTest` "a RACE cup needs two racers and a PRACTICE cup needs one" (`feat(race)`)
- [ ] 1.2 Red: `StartGateTest` "an explicit minimum overrides the mode for both modes" (`feat(race)`)

## 2. Green: the start gate reads the mode

- [ ] 2.1 Green: `StartGate` takes the cup's `GameMode` and reads `minimumPlayers()` unless an explicit minimum is set (`feat(race)`)

## 3. Red: the solo wait

- [ ] 3.1 Red: `SoloWaitTest` "one racer waiting for the full wait starts the practice cup, counted in ticks" (`feat(race)`)
- [ ] 3.2 Red: `SoloWaitTest` "a second racer before the wait elapses cancels the solo start" (`feat(race)`)
- [ ] 3.3 Red: `SoloWaitTest` "a wait of 0 never starts a practice cup" (`feat(race)`)
- [ ] 3.4 Red: `SoloWaitTest` "the wait is counted in ticks, so 30 seconds is 600 ticks" (`feat(race)`)

## 4. Green: the solo wait

- [ ] 4.1 Green: `SoloWait` record in `voyager/race` (`race.flow`), a pure tick counter with no Minestom import (`feat(race)`)

## 5. Red: the practice grid

- [ ] 5.1 Red: `PracticeGridTest` "a practice start fills the grid to the practice count, ignoring the RACE count" (`feat(race)`)
- [ ] 5.2 Red: `PracticeGridTest` "a RACE start keeps the RACE count and creates no practice computer racer" (`feat(race)`)
- [ ] 5.3 Red: `PracticeGridTest` "a practice count below one is refused" (`feat(race)`)

## 6. Green: the practice grid

- [ ] 6.1 Green: `PracticeGrid` record in `voyager/race` (`race.cup`) and the grid fill call for the practice start (`feat(race)`)

## 7. Red: settings and configuration check

- [ ] 7.1 Red: `ServerSettingsTest` "`VOYAGER_SOLO_WAIT_SECONDS` defaults to 30 and `VOYAGER_PRACTICE_COM_COUNT` to 4" (`feat(race)`)
- [ ] 7.2 Red: `ConfigCheckRunTest` "check mode exits 1 for a non-numeric solo wait and for a practice count below one" (`feat(race)`)

## 8. Green: settings

- [ ] 8.1 Green: the two keys added to `ServerSettings.KEYS`, read through the configuration loader in `server.config`, and named in
  `ConfigCheck` (`feat(race)`)

## 9. Red: the lobby's solo start and the join rule

- [ ] 9.1 Red: `WaitingRoomSoloTest` "a lone racer waits alone for the solo wait, then a practice cup starts with the practice grid" (`feat(race)`)
- [ ] 9.2 Red: `WaitingRoomSoloTest` "a second racer during the solo wait starts the RACE countdown and no practice cup" (`feat(race)`)
- [ ] 9.3 Red: `WaitingRoomSoloTest` "leaving the only racer of a practice cup aborts it without a result" (`feat(race)`)

## 10. Green: the lobby's solo start

- [ ] 10.1 Green: `WaitingRoom` ticks `SoloWait` while exactly one racer waits and starts the practice cup when it elapses (`feat(race)`)

## 11. Red: ranked honesty

- [ ] 11.1 Red: `PracticeRankingTest` "a practice finish writes no ranked statistic and no cup points" (`feat(race)`)
- [ ] 11.2 Red: `PracticeRankingTest` "a RACE finish still writes its ranked statistic" (`feat(race)`)

## 12. Green: ranked honesty

- [ ] 12.1 Green: statistics and cup standings skip runs whose cup mode is not ranked (`feat(race)`)

## 13. Red: labels and tutorial

- [ ] 13.1 Red: `LobbyLabelTest` "a RACE lobby shows `Ranked`, a practice lobby shows `Practice · not ranked`, each in the racer's language" (`feat(race)`)
- [ ] 13.2 Red: `TutorialRankedLineTest` "the first tutorial page carries the ranked line for a RACE cup and the practice line for the practice cup" (`feat(race)`)
- [ ] 13.3 Red: `MessageBundleParityTest` fails until the new keys exist in every bundled language (`feat(race)`)

## 14. Green: labels and tutorial

- [ ] 14.1 Green: the label keys in `voyager_en_US.properties` and every other bundle; the sidebar title and the first tutorial page read them (`feat(race)`)

## 15. Red then Green: the shipped practice cup

- [ ] 15.1 Red: `ShippedCatalogueTest` "the shipped catalogue lists exactly three cups, with `elytrarace:solo_practice` in mode PRACTICE on the alpha map" (`feat(race)`)
- [ ] 15.2 Green: `cups/solo_practice.json` with mode `PRACTICE` and `mapNames` of `elytrarace:blue_and_red` (`feat(race)`)

## 16. Verify the golden transcripts

- [ ] 16.1 Replay the cup-session golden transcripts with `VOYAGER_COM_COUNT` 0 and confirm each is byte-identical; record the run in the code pull request
  description (`feat(race)`)
- [ ] 16.2 Run `:voyager:fitness:test` and `:voyager:race:test` through `heavy` with `--max-workers=2` (`feat(race)`)

## 17. Docs

- [ ] 17.1 Update the operator page for the three new or changed settings and the solo start in `docs/` (`docs`)
- [ ] 17.2 Write a reference entry in `docs/reference/` on the practice start, its labels and its ranked rule, so the Diátaxis
  reference matches the behaviour (`docs`)

## 18. Open the code pull request

- [ ] 18.1 Open the code pull request with the title `feat(race): start a solo practice run for a lone racer`, from a branch based on
  the remote main branch that contains the merged proposal, with no file under `openspec/` (`feat(race)`)
