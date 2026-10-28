# Tasks

Commit type and scope: `feat(race)` for the code and its tests, `docs` for the `docs/` pages. Each behaviour gets a failing test
(Red) before its production code (Green). Every test follows F.I.R.S.T.: no sleeps, time comes from an injected clock or an explicitly
ticked race clock, a fresh fixture per test, no shared static state. The personal best read is a fake that the test completes by hand,
not a real database. Task checkboxes are ticked in the follow-up pull request, never in the code pull request.

This change starts after `smooth-map-transitions` has landed, because racers stand in the next map while the wait runs.

## 1. Red: the countdown, the standings and the place line

- [ ] 1.1 Red: `SecondsUntilGoTest` "the seconds to GO are the END remainder plus the lobby remainder, rounded up" (`feat(race)`)
- [ ] 1.2 Red: `MapStandingTest` "finishers are ordered by finish time with gaps to the leader, racing racers come next, DNF racers
  last" (`feat(race)`)
- [ ] 1.3 Red: `MapStandingTest` "a racer alone on the map has no gap, and computer racers are in the list" (`feat(race)`)
- [ ] 1.4 Red: `PersonalBestComparisonTest` "a faster run is a new best with its delta, a slower run is the delta to the best,
  an equal run is equal, and no stored best is a first run" (`feat(race)`)
- [ ] 1.5 Red: `CupAnnouncerStandingTest` "the place line reads place, out of every racer who started, with the gap to the racer
  ahead, or behind for first place" (`feat(race)`)
- [ ] 1.6 Red: `CupAnnouncerStandingTest` "a computer racer ahead is named in the gap, and a computer racer gets no line"
  (`feat(race)`)
- [ ] 1.7 Red: `CupAnnouncerStandingTest` "a racer who did not finish gets no place line" (`feat(race)`)
- [ ] 1.8 Red: `CupAnnouncerStandingTest` "with statistics off, no line about the map contains the word best" (`feat(race)`)
- [ ] 1.9 Red: `CupAnnouncerStandingTest` "a ranked human finish with statistics on carries the personal best clause, and an
  unranked finish does not" (`feat(race)`)
- [ ] 1.10 Red: `CupSessionPersonalBestTest` "the best is requested at GO, and a finish before the answer omits the clause"
  (`feat(race)`)
- [ ] 1.11 Red: `WaitTitleTest` "the boss bar counts down from the full wait, and is absent before the first map and after the last"
  (`feat(race)`)
- [ ] 1.12 Red: `WaitTitleTest` "a racer placed in the next map before the phase ends reads 'Waiting for n racers', and the title
  switches to the count when the phase ends" (`feat(race)`)
- [ ] 1.13 Red: `BetweenMapsBoardTest` "the sidebar lists the racing racers and computer racers, holds at most fifteen lines, and
  ends with '+ n more' when racers overflow" (`feat(race)`)
- [ ] 1.14 Red: `StartCountdownUnchangedTest` "in the last three seconds before GO the start countdown's title and beat are the only
  title and sound" (`feat(race)`)
- [ ] 1.15 Red: run 1.1 to 1.14 and confirm each fails for the stated reason; record the failure messages in the code pull request
  (`feat(race)`)

## 2. Green: the countdown, the standings and the place line

- [ ] 2.1 Green: `race/standing` with `MapStanding` and `PersonalBestComparison`, pure, with `package-info` @NotNullByDefault
  (`feat(race)`)
- [ ] 2.2 Green: `race/flow` gives the seconds to GO from `RaceState` and `RaceTimings` (`feat(race)`)
- [ ] 2.3 Green: `CupAnnouncer.announceMapScore` sends the place line after the breakdown, to human racers only, with the personal
  best clause only when the caller passes a best (`feat(race)`)
- [ ] 2.4 Green: `CupSession` starts the personal best read at the map's GO for each ranked human racer, bounded to two seconds, and
  passes an absent result for a late read (`feat(race)`)
- [ ] 2.5 Green: `RaceHud` shows "Next map in N s" from the phase end, and "Waiting for n racers" before it for a racer already in
  the next map (`feat(race)`)
- [ ] 2.6 Green: the standings sidebar in `lobby/`, recomputed each second, removed at GO (`feat(race)`)
- [ ] 2.7 Green: the new keys in `voyager_en_US` and `voyager_de_DE` with `<arg:N>` placeholders, so that the parity test passes
  (`feat(race)`)
- [ ] 2.8 Refactor: confirm 1.1 to 1.14 pass, and that the existing `CupAnnouncer`, `RaceHud` and `race-feedback` tests are unchanged
  and still pass (`feat(race)`)

## 3. Docs

- [ ] 3.1 Update `docs/reference/hud.md`: the countdown in the boss bar, the waiting title, the standings sidebar, and the place line
  with its personal best rule (`docs`)
- [ ] 3.2 Update `docs/reference/message-style-guide.md`: the place line's wording in both languages, and the rule that no line says
  "best" when statistics are off (`docs`)

## 4. Checks

- [ ] 4.1 Run `./gradlew :voyager:race:test :voyager:platform:test :voyager:fitness:test` (`feat(race)`)
- [ ] 4.2 Run `./gradlew build` for both trees (`feat(race)`)
- [ ] 4.3 Smoke-boot the server shadow JAR with LuckPerms as the apply rules require, and quote the start lines in the code pull
  request (`feat(race)`)
- [ ] 4.4 Playtest on palmbeach, dragonlobby and memoria, with statistics on and then off: record the place line, the personal best
  clause, and the latency of the best read (`feat(race)`)

## 5. Ship

- [ ] 5.1 Open the code pull request under the title `feat(race): show each racer's place, gap and best time after a map, and count down to the next one`.
  Write the description as the racer sees the wait: the count to the next map, who finished and by how much, and the line that says
  where they finished and whether it was a new best. Include the Red messages (1.15), the smoke lines (4.3) and the playtest record
  (4.4). Do not add a session link (`feat(race)`)
