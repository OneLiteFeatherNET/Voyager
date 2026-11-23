# Design

## Dependency direction

Ring progress is pure domain logic in `voyager-race` (`net.elytrarace.voyager.race`). `CupSession` in `voyager-platform` supplies
the positions and resets the racer; it does not decide the progress. The dependency stays one way: platform -> race.

```
platform: CupSession, RunResetter  --calls-->  race: ProgressTracker, RingPass (pure)
```

## Changes by module

| Module | Type or method | Change |
|---|---|---|
| `voyager-race` | `RingPass` | Add `OptionalDouble crossingParameter(Vec3 from, Vec3 to, Ring ring)`, returning the parameter in [0, 1] along the segment where the ring's plane is crossed inside the ring's area. `crosses` delegates to it, so its behaviour is unchanged. |
| `voyager-race` | `ProgressTracker.advance` | After the next ring is crossed, keep testing the following ring against the same segment while its parameter is not earlier than the previous one's. Credit each. Stop at the first ring not crossed. The existing `missedRing` branch is unchanged for the case where the next ring is not crossed. |
| `voyager-platform` | `CupSession` | Clear `lastPositions` and `lastVelocities` for the racer inside the reset path, after `RunResetter.reset`, and before the next tick reads them. Today the clear happens only on map change and leave (`CupSession.java:1687`, `1702`). |

`RunResetter` keeps its signature; the clear is done by `CupSession`, which owns the position maps.

## Ordering rule

Credit order is the crossing parameter, not list position. For rings `k` and `k+1` crossed in the same segment, `k` is credited
first when its parameter is less than or equal to `k+1`'s. This is the rule the new `race/ring-effects` requirement states, so the
effects compose in the order the rings are crossed (already required by the existing "Effects in quick succession" requirement).

## Boundaries and fitness

- No new boundary. `RingPass` and `ProgressTracker` import no Minestom or platform type; the existing `voyager-fitness` rules for
  `voyager-race` cover them.
- `CupSession` keeps its existing dependencies. The clear is a map operation on fields it already owns.

## Decisions

No ADR. The rule changes a stated test expectation (`countsAtMostOneRingPerTick`); the owner confirms it in the proposal's open
questions before the test is replaced.
