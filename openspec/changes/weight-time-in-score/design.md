# Design

## Context

- `MapScorer.score(RingProgress, MapDefinition, Duration)` (`voyager/race/.../scoring/MapScorer.java:40-46`) sums ring points and
  adds the medal's points from `MedalBrackets.DEFAULT.classify(elapsed, referenceTime)`. The medal values live on
  `MedalTier` (`voyager/api/.../race/MedalTier.java:11-16`).
- `MedalBrackets` multipliers are 1.00, 1.10, 1.25 and 1.50 of the reference time (`voyager/api/.../race/MedalBrackets.java:14`).
- `PlacementBonus` is a separate, ranked-only award (`voyager/race/.../scoring/PlacementBonus.java:33-36`).
- ADR-0003 (`docs/decisions/0003-race-mode-time-bracket-scoring.md`) fixes the brackets and forbids rebalancing the other parts.

## Decisions

### 1. Record a superseding MADR before the code

The owner chooses Option A or B and the share. A new MADR record (`docs/decisions/` next free number) states the choice and
marks ADR-0003's "no rebalancing" consequence as superseded. Code starts only after it is accepted. This is the
"Architecture decisions get a MADR ADR" rule in `openspec/config.yaml`.

### 2. Option B: values only, in `voyager/api`

`MedalTier` values change; `MedalBrackets` keeps its boundaries. `voyager/api` stays free of implementation, so the change is
data-only. The medal spread becomes the target share minus the placement spread.

### 3. Option A: a time pool in `voyager/race`

A new pure type `TimePool` (record, `voyager/race`, package `net.elytrarace.voyager.race.scoring`) holds the map's maximum time
component and computes `pointsFor(Duration elapsed, Duration reference)`. It is pure, takes no clock, and is `@NotNullByDefault`.
It is not placed in `voyager/api` because it is a scoring rule, not a published contract; the API keeps the medal enum only.

Dependency direction: `api` (MedalTier, MapDefinition) <- `race` (MapScorer, TimePool) <- `platform` (CupAnnouncer, wording).
Nothing points back.

### 4. Fitness

- `voyager-fitness`: the existing rule that `race` imports no Minestom class covers `TimePool`. No new boundary, so no new rule.
- A new test in `voyager/fitness` is not needed; the time component is verified by unit tests over `MapScorer`.

## Verification plan

- Property-style unit tests (JUnit 6, AssertJ) with fixed `Duration` values, no clock: monotonicity, non-negativity, cap, DNF zero,
  computer racers zero. Each test builds its own `MapDefinition` fixture.
- A regression test fixes the historic totals for one stored map under the old rule, so the decision to keep or recompute stored
  results is visible in the diff.

## Risks

- A reference time set wrongly amplifies the error into the score; the calibration change must land first.
- The HUD can show the medal but not the continuous pool; the chat breakdown must name the time component so the number is not a
  mystery.
