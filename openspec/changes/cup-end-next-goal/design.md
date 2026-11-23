# Design

## Context

- The cup's final standings are announced by `CupAnnouncer.announceCupResult` (`platform/cup/CupAnnouncer.java:205-240`), which
  sends the block to each racer with their own line marked.
- `CupScore` (`voyager/race/src/main/java/net/elytrarace/voyager/race/scoring/CupScore.java:15`) holds total points, best time,
  maps finished and rings passed. It does not hold each map's medal or time, so the weakest map cannot be read from it.
- A per-map medal and time is produced at the map's finish (`MapScore`, `MapScorer`), and `CupSession.mapFinished` hands each
  finished map to the recorder (`platform/cup/CupSession.java`). That is where the per-map results are available.
- `MedalCountdown.nextGoal(tier, completion, reference, brackets)` (`voyager/race/.../scoring/MedalCountdown.java:89`) gives the
  next medal and the seconds saved. It is pure and already used by the map result subtitle.

## Decisions

### 1. Keep the per-map results for the round in `race`, not in the platform

A pure value `RoundResults` (record, `voyager/race`, package `net.elytrarace.voyager.race.cup`) holds, for each human racer, the
medal and the completion time of each map they finished, and whether they did not finish. It is filled by `CupSession` from the
`MapScore` it already produces and is read at the cup's end. It is not persisted: it lives for one cup round, like `CupStandings`.

Dependency direction: `api` <- `race` (`RoundResults`, `WeakestMap`, `MedalCountdown`) <- `platform` (`CupSession` fills it,
`CupAnnouncer` sends the line). No Minestom import enters `race`.

### 2. `WeakestMap` is a pure selection

`WeakestMap.of(RoundResults, racer, armedMaps)` returns the map with the lowest medal among the racer's finished maps that are in
the armed rotation, ties broken by the earliest map in the rotation. It returns empty when no such map exists or when every map is
a Diamond. Pure, `@NotNullByDefault`, tested without a server.

### 3. Fitness

- The existing `race` rule (no Minestom import) covers the two new types. No new boundary, so no new `voyager-fitness` rule.
- `CupAnnouncer` keeps the message keys; the line is built from `Messages`, as the other cup lines are.

## Risks

- The armed rotation is read at the end of the round; a rotation change in the middle of a round must not change the line for that
  round. The test pins the rotation at the round's start.
- Seconds are rounded up so that the line never promises a time the racer cannot reach by rounding to a lower value.
