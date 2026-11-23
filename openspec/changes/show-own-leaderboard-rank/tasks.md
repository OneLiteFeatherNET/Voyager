# Tasks

Commit type and scope: `feat(stats)` for the code and its tests, `docs` for `docs/reference/`. Each behaviour gets a failing test
(Red) before its production code (Green). Every test follows F.I.R.S.T.: the reader port is faked per test, no database in the unit
suite, no clock, a fresh fixture per test, no shared static state.

## 1. Red: the own line

- [ ] 1.1 Red: `RankTest` "a place below one or above the pool size is refused" (`feat(stats)`)
- [ ] 1.2 Red: `StatsRepliesRankTest` "a caller outside the top ten gets the place line after the ten entries" (`feat(stats)`)
- [ ] 1.3 Red: `StatsRepliesRankTest` "a caller inside the top ten gets no own line" (`feat(stats)`)
- [ ] 1.4 Red: `StatsRepliesRankTest` "a caller with no best gets no own line" (`feat(stats)`)
- [ ] 1.5 Red: `StatsRepliesRankTest` "the percentile is shown for a pool of fifty or more and omitted below" (`feat(stats)`)
- [ ] 1.6 Red: `StatsRepliesRankTest` "the percentile rounds to the nearest whole per cent, so 34 of 212 reads as top 16 per cent" (`feat(stats)`)
- [ ] 1.7 Red: `StatsRepliesRankTest` "racers with equal times share a place" (`feat(stats)`)
- [ ] 1.8 Red: `RankQueryIntegrationTest` "the count uses the same layout and ranked filters as the top-N query" (`feat(stats)`)
- [ ] 1.9 Red: `LeaderboardReadTimeoutTest` "an unavailable rank read falls back to the top ten alone, within two seconds" (`feat(stats)`)
- [ ] 1.10 Red: run 1.1 to 1.9 and record each failure message in the code pull request (`feat(stats)`)

## 2. Green: the own line

- [ ] 2.1 Green: `Rank` record in `voyager/api` `net.elytrarace.voyager.api.stats`, with `package-info` @NotNullByDefault (`feat(stats)`)
- [ ] 2.2 Green: the count query in `PostgresStatsReader`, one statement, the same filters as `topTimes` (`feat(stats)`)
- [ ] 2.3 Green: `StatsReplies` appends the own line and the percentile, from `Rank` (`feat(stats)`)
- [ ] 2.4 Green: the command handler asks for the caller's rank only when they are outside the top ten, under the two-second read
  budget (`feat(stats)`)
- [ ] 2.5 Green: the new keys in `voyager_en_US` and `voyager_de_DE` with `<arg:N>` placeholders, so the parity test passes
  (`feat(stats)`)
- [ ] 2.6 Refactor: confirm 1.1 to 1.9 pass and the existing `StatsRepliesTest` passes without edits (`feat(stats)`)

## 3. Verify

- [ ] 3.1 Run the persistence integration test for the count query and the race and platform unit tests (`feat(stats)`)

## 4. Docs

- [ ] 4.1 Update `docs/reference/` with the leaderboard's own line, its pool minimum and its rounding (`docs`)

## 5. Pull request

- [ ] 5.1 Open the code pull request titled `feat(stats): show the caller's place below the leaderboard top ten` from the
  implementation branch, with no file under `openspec/` (`feat(stats)`)
