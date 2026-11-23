# Proposal

**Type and scope:** `feat(stats)`. Ships as one code change under this title. When a racer runs `/leaderboard <map>` and is not in
the top ten, the reply adds one line with their own place and, once the pool is large enough, their percentile. It does not change
the top ten, the stored times, the ranked rule or the permission model.

**Vertical slice:** `stats` (the leaderboard read and its reply), with one new query in `persistence`.

## Why

`/leaderboard <map>` lists the top ten personal bests of the current layout, fastest first
(`openspec/specs/stats/player-stats/spec.md`, "`/leaderboard` lists the best times of the current layout", lines 75-82). The reply
is built in `voyager/platform/src/main/java/net/elytrarace/voyager/platform/stats/StatsReplies.java:71-78`, which limits the list to
`LEADERBOARD_LENGTH = 10` (`StatsReplies.java:22`). A racer outside the top ten sees the list and no sign of where they stand.
Most racers are in the bottom half of any pool, so for most of them the reply says nothing about their own progress.

The reader already answers the top-N query (`voyager/persistence/src/main/java/net/elytrarace/voyager/persistence/PostgresStatsReader.java:133-138`,
`topTimes(mapKey, layoutVersion, mode, limit)`). No query yet counts how many racers are faster than the caller.

## What Changes

- **Own line.** When the caller has a ranked personal best on the map's current layout and is outside the top ten, the reply
  appends one line: "You: #34 of 212, top 16%".
- **Minimum pool.** The percentile is shown only when the pool has at least 50 racers with a best time. Below that, the line
  reads "You: #11 of 12" without a percentile, so a small pool does not look like a verdict.
- **Rounding.** The percentile is the rank divided by the pool, rounded to the nearest whole per cent, shown as "top N%". A place that rounds to 0 is shown as "top 1%".
- **No line when inside the top ten** (the list already shows the place), and no line when the caller has no best.
- **New query.** A count of the racers whose best on the layout is faster than the caller's, and the pool size, read in one
  statement.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `stats/player-stats`: "`/leaderboard` lists the best times of the current layout" gains the caller's own line when outside the
  top ten.

## Trade-offs

### Pros

- A positive frame for the bottom half of a pool: "top 16%" is a fact a racer can be proud of.
- Reuses the persistence read and the reply's existing line builder; the change is one line and one query.

### Cons

- Small pools look bad: "#9 of 9" can read as last place. The minimum pool guards this but does not remove it.
- Needs a new rank query against the database, so it adds load to the read path; the two-second read limit still applies.
- Percentiles over a pool of one layout ignore that a racer may be strong on other maps.

## Out of scope

- Global or cross-map ranks, and the `/stats` profile (`player-stats`, "`/stats` shows a player's profile").
- Any change to the top ten or to the ranked rule.
- Publishing ranks to other racers or to the scoreboard.

## Open questions

- Owner: is 50 the right minimum pool, or should it be higher to avoid a percentile on a near-empty map?
- Owner: should the line also show when the caller is inside the top ten, as "You: #3 of 212"? Proposed: no, the list already shows it.

## Impact

- `voyager/persistence`: a rank query in `PostgresStatsReader`, one statement, the layout and mode fixed as in `topTimes`.
- `voyager/api`: a `Rank` record (place, pool size) in `net.elytrarace.voyager.api.stats`, no implementation.
- `voyager/platform`: `stats/StatsReplies` (the own line and the percentile) and the stats reader port that `NoOpStatsReader` implements.
- `docs/reference/` for the leaderboard reply.
