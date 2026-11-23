# Proposal

**Type and scope:** `feat(race)`. Ships as one code change under this title. It changes how much of a map's points depend on
the time flown. It does not change the ring points, the placement bonus, the medal brackets' boundaries, the DNF rule, or the
cup's standings arithmetic.

**Vertical slice:** `race` (scoring: `race/scoring/MapScorer`, `race/scoring/PlacementBonus`, the medal values in the API).

**Decision altered:** ADR-0003 ("Race Mode uses fixed time-bracket scoring per map") states that the bracket score "sits alongside
the existing ring and position bonus, so no existing scoring code is rewritten" and that the scoring must "compose with the
existing ring points and position bonus without rebalancing them". This change rebalances them. It therefore needs a new MADR
record that supersedes ADR-0003 in part, approved by the owner before the code change starts.

## Why

Speed is barely rewarded. A map's points are the ring points (10 each, `Ring.points()`, summed in
`voyager/race/src/main/java/net/elytrarace/voyager/race/scoring/MapScorer.java:54-61`), the placement bonus (10, 6, 3, 1 for
places 1 to 3 and the rest, `voyager/race/src/main/java/net/elytrarace/voyager/race/scoring/PlacementBonus.java:33-36`), and the
medal (DIAMOND 60, GOLD 45, SILVER 30, BRONZE 15, FINISH 5, DNF 0; `voyager/api/src/main/java/net/elytrarace/voyager/api/race/MedalTier.java:11-16`).
Only the medal depends on time, and the time-dependent part of a racer's total is at most 64 points: a medal spread of 55 (60
down to 5) plus a placement spread of 9 (10 down to 1). On the shipped `blue_and_red`, the maximum is 420 points, so the time
decides about 15 per cent of it. A racer who flies the map in half the reference time earns the same as one who finishes at
twice it, apart from the medal's step.

The effect also does not scale with map length. A long map and a short map give the medal the same 55-point spread, although a
long map has more time in it to save.

## What Changes

- **Time share.** The points a map can award for time (medal plus its share of the time component) become 25 to 35 per cent of
  the map's total maximum. The exact share is an owner decision (see Open questions).
- **Option A: per-map time pool.** A map has a time pool, a share of its maximum. A finisher earns the pool times the ratio of
  the reference time to their time, capped at the pool, so faster is worth more and slower is worth less, continuously and not
  only in brackets. The medal word stays, driven by the brackets as now.
- **Option B: larger medal values.** Keep the brackets and raise the medal values until the medal spread reaches the target
  share. No new formula; the change is in the values only.
- **BREAKING (scoring meaning):** the totals in existing cup results and in `player-stats` change meaning for every map. Stored
  results are not rewritten; the change applies to races after the code ships. The owner must agree that the historic totals
  are kept as they were.
- **Documentation.** The tutorial's medal page (`voyager_en_US.properties` tutorial page 5) and `docs/reference/` say what a
  racer's points are made of.

## Capabilities

### New Capabilities

- `race/map-scoring`: how a map's points are made of ring points, placement, and the time component, and the rule that the time
  component is capped and never negative. No main spec currently owns scoring; the rules live in ADR-0003 and in the code, so the
  requirements are new here rather than a modification of `race/map-result-standing` (which covers the place line only) or
  `race-feedback` (which covers the chat breakdown only).

### Modified Capabilities

None. The chat breakdown in `race-feedback` shows the same parts; its wording does not change.

## Trade-offs

### Pros

- Speed is visibly rewarded, and the reward scales with the map's length.
- Option A keeps the brackets' legibility on the HUD and adds a continuous signal behind them.
- Option B is a value change with no new formula, so it is small and easy to revert.

### Cons

- The meaning of existing results changes; any leaderboard or cup standing built on old totals shifts.
- Until reference times are calibrated, a bad reference time amplifies its error: a reference set too slow gives everyone a large
  time share, and one set too fast gives almost nobody any.
- Option A adds a formula the HUD must explain in one line, which ADR-0003 argued against.

## Dependencies

- Calibrated reference times. This change should follow the catalogue calibration. The name `calibrate-shipped-catalogue` does
  not appear in `openspec/` or `docs/` or in the git history on this branch, so its status must be confirmed before scheduling.

## Out of scope

- Auto-tuning reference times to the playerbase median (ADR-0003 names it as a separate epic).
- Any change to the ring points, the placement bonus or the DNF rule.
- Rewriting stored results.

## Open questions

- Owner: Option A (per-map continuous time pool) or Option B (larger medal values)? Recommendation: B first, because it is a
  value change that the playtests can judge, and A only if B's steps feel too sharp.
- Owner: target share of the time component, 25, 30 or 35 per cent?
- Owner: keep historic totals unchanged, or recompute stored results under the new rule?

## Impact

- `voyager/api`: `MedalTier` values (Option B) or a new time-pool record (Option A, `race` only).
- `voyager/race`: `MapScorer` (time component), `PlacementBonus` unchanged.
- `voyager/platform`: the chat breakdown line (`cup/CupAnnouncer`), wording only.
- `docs/decisions/`: a new MADR record superseding ADR-0003 in part.
- `docs/reference/` and the tutorial keys in `voyager_en_US` and `voyager_de_DE`.
