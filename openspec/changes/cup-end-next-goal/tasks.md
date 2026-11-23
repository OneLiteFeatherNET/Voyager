# Tasks

Commit type and scope: `feat(race)` for the code and its tests, `docs` for `docs/reference/message-style-guide.md`. Each behaviour
gets a failing test (Red) before its production code (Green). Every test follows F.I.R.S.T.: fixed medals and `Duration` values,
the rotation pinned in the fixture, a fresh `RoundResults` per test, no shared static state.

This change does not start until the owner has agreed to amend "Nothing in the end screen is added beyond the named elements".

## 1. Red: the next goal

- [ ] 1.1 Red: `WeakestMapTest` "the weakest map is the lowest medal among the finished maps in the armed rotation" (`feat(race)`)
- [ ] 1.2 Red: `WeakestMapTest` "a tie on medal is broken by the earliest map in the rotation" (`feat(race)`)
- [ ] 1.3 Red: `WeakestMapTest` "a map the racer did not finish is not chosen" (`feat(race)`)
- [ ] 1.4 Red: `WeakestMapTest` "no map is chosen when every map is a Diamond or none is in the rotation" (`feat(race)`)
- [ ] 1.5 Red: `NextGoalLineTest` "the seconds to the next medal are rounded up to a whole second" (`feat(race)`)
- [ ] 1.6 Red: `NextGoalLineTest` "a computer racer gets no line, and a human racer gets at most one" (`feat(race)`)
- [ ] 1.7 Red: `CupAnnouncerNextGoalTest` "the line follows the standings block, is chat only, and carries no click action"
  (`feat(race)`)
- [ ] 1.8 Red: `CupSessionRoundResultsTest` "the per-map results of the round are kept until the cup's end, and a map that was
  not finished is recorded as not finished" (`feat(race)`)
- [ ] 1.9 Red: run 1.1 to 1.8 and record each failure message in the code pull request (`feat(race)`)

## 2. Green: the next goal

- [ ] 2.1 Green: `RoundResults` and `WeakestMap` in `voyager/race` `net.elytrarace.voyager.race.cup`, pure, `package-info`
  @NotNullByDefault (`feat(race)`)
- [ ] 2.2 Green: `CupSession` fills `RoundResults` from each map's `MapScore` and pins the armed rotation at the round's start
  (`feat(race)`)
- [ ] 2.3 Green: `CupAnnouncer.announceCupResult` sends the next-goal line after the standings, to human racers only (`feat(race)`)
- [ ] 2.4 Green: the new keys in `voyager_en_US` and `voyager_de_DE` with `<arg:N>` placeholders, so the parity test passes
  (`feat(race)`)
- [ ] 2.5 Refactor: confirm 1.1 to 1.8 pass, and that the existing `CupAnnouncer` and `CupStandings` tests pass without edits
  (`feat(race)`)

## 3. Verify

- [ ] 3.1 Run `./gradlew :voyager:race:test :voyager:platform:test :voyager:fitness:test` and confirm green (`feat(race)`)

## 4. Docs

- [ ] 4.1 Update `docs/reference/message-style-guide.md` with the next-goal line in both languages (`docs`)

## 5. Pull request

- [ ] 5.1 Open the code pull request titled `feat(race): name the next medal on the weakest map at the cup end` from the
  implementation branch, with no file under `openspec/` (`feat(race)`)
