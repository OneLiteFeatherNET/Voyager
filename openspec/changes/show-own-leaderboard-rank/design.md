# Design

## Context

- `StatsReplies.leaderboard(mapName, entries)` builds the reply from the entries the reader gives, fastest first, at most ten
  (`voyager/platform/.../stats/StatsReplies.java:71-78`). It has no caller identity today.
- `PostgresStatsReader.topTimes` (`voyager/persistence/.../PostgresStatsReader.java:133-138`) filters ranked modes and the layout.
  It is the model for the new count query: same filters, same two-second read budget (`player-stats`, "A read answers within two
  seconds or says it cannot").
- Personal bests are stored per player, map, layout and mode (`player-stats`, "The personal best of a map keeps the fastest time").

## Decisions

### 1. One count query, the same filters as the top-N query

A single statement returns the number of players whose best on the layout is strictly faster than the caller's, and the number of
players with any best. The layout and the ranked modes are the same filters as `topTimes`, so the own line and the top ten cannot
disagree about which runs count.

Equal times share a place: the place is one plus the number faster, so "#34" means 33 are faster.

### 2. `Rank` is a value in `voyager/api`

`Rank(int place, int poolSize)` is a record in `net.elytrarace.voyager.api.stats` with a compact constructor that rejects a place
below one or above the pool size. It has no implementation, so it stays in the API module's rule set. The percentile is computed in
`StatsReplies`, which is pure, from `place` and `poolSize`.

### 3. Dependency direction

`api` (Rank, LeaderboardEntry) <- `persistence` (the count query, implements the reader port) <- `platform` (`StatsReplies` composes
the line; the reader is called from the command handler). `platform` never reads the table itself.

### 4. Fitness

- The existing `api` rule (no implementation) covers `Rank`.
- The existing `persistence` integration test layout (`src/integrationTest`) covers the query; it is not part of the unit suite.
  The unit suite fakes the reader port.
- No new `voyager-fitness` rule is needed.

## Risks

- The query runs on the read path; it must stay inside the two-second read budget, and an unavailable answer falls back to the
  top ten alone, without the own line.
- Ties: two racers with the same best share a place; the test pins this so the percentile is not inflated.
