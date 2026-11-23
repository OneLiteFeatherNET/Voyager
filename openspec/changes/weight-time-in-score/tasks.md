# Tasks

Commit type and scope: `feat(race)` for the scoring code and its tests, `docs` for the decision record and `docs/reference/`. Each
behaviour gets a failing test (Red) before its production code (Green). Every test follows F.I.R.S.T.: fixed `Duration` values, no
clock, a fresh `MapDefinition` fixture per test, no shared static state.

This change does not start until the owner has chosen Option A or B and the share, and a MADR record superseding ADR-0003 in part
is accepted (see design.md, Decision 1), and after the reference-time calibration has landed.

## 0. Decision

- [ ] 0.1 Write a MADR record in `docs/decisions/` that supersedes ADR-0003's "no rebalancing" consequence, with the option and share
  the owner chose (`docs`)
- [ ] 0.2 Owner accepts the record before any code task starts (`docs`)

## 1. Red: the time component

- [ ] 1.1 Red: `MapScorerTimeTest` "a faster finish never scores less time than a slower one on the same map" (`feat(race)`)
- [ ] 1.2 Red: `MapScorerTimeTest` "the time component is never negative, even for a finish at three times the reference time"
  (`feat(race)`)
- [ ] 1.3 Red: `MapScorerTimeTest` "the time component never exceeds the map's maximum time component" (`feat(race)`)
- [ ] 1.4 Red: `MapScorerTimeTest` "a DNF earns no time component and the medal is DNF" (`feat(race)`)
- [ ] 1.5 Red: `MapScorerTimeTest` "a computer racer's finish earns no time component" (`feat(race)`)
- [ ] 1.6 Red: `MapScorerTimeTest` "the shipped course's maximum time component is between 25 and 35 per cent of its maximum total"
  (`feat(race)`)
- [ ] 1.7 Red: `HistoricTotalsTest` "a stored result of one fixed map scores the same under the rule it was recorded with" (`feat(race)`)
- [ ] 1.8 Red: run 1.1 to 1.7 and record each failure message in the code pull request (`feat(race)`)

## 2. Green: the time component

- [ ] 2.1 Green: `TimePool` (Option A) or the revised `MedalTier` values (Option B), per the accepted record, pure, with
  `package-info` @NotNullByDefault (`feat(race)`)
- [ ] 2.2 Green: `MapScorer` adds the time component and leaves ring points and placement as they are (`feat(race)`)
- [ ] 2.3 Green: the chat breakdown names the time component in `CupAnnouncer`, with the keys in `voyager_en_US` and `voyager_de_DE`
  (`feat(race)`)
- [ ] 2.4 Refactor: confirm 1.1 to 1.7 pass and the existing `MapScorerTest` and `PlacementBonus` tests are unchanged (`feat(race)`)

## 3. Verify

- [ ] 3.1 Run the targeted race tests and the fitness module (`feat(race)`)
- [ ] 3.2 Playtest the shipped course with the owner, record the result in the decision record (`docs`)

## 4. Docs

- [ ] 4.1 Update `docs/reference/` for how a map's points are made of, and the tutorial medal page keys (`docs`)

## 5. Pull request

- [ ] 5.1 Open the code pull request titled `feat(race): weight time in the map score` from the implementation branch, with no file
  under `openspec/` (`feat(race)`)
