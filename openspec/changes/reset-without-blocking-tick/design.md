# Design

## Dependency direction

The reset decision stays in `voyager-race` (`RunReset.plan`, pure). The reset placement is an adapter concern in `voyager-platform`:
`CupSession` -> `RunResetter` -> `MapTransition` -> `PendingArrival` / `Arrivals`. The new state (a pending reset per racer) lives
in `voyager-platform` next to the existing per-racer arrival state; `voyager-race` gains no type.

```
race: RunReset (pure plan)
platform: CupSession --> RunResetter --> PendingResetPlacement (new, per racer) --> PendingArrival (existing, non-blocking)
```

## Changes by module

| Module | Type or method | Change |
|---|---|---|
| `voyager-platform` | `RunResetter.reset` | Instead of calling `MapTransition.reposition` (blocking), it registers a pending reset with `PendingResetPlacement` and returns. |
| `voyager-platform` | `PendingResetPlacement` (new, package-private, `cup` package) | Holds one pending reset per racer. Starts the preload through `PendingArrival`; on each tick, applies the reset when `PendingArrival` reports the chunks present. Replaces the pending target on a second reset. |
| `voyager-platform` | `Arrivals.reposition` | Loses its reset caller. If no caller remains after the change, the method is removed with its baseline entry; if a caller remains (for example a test helper), it stays and the baseline entry is kept. |
| `voyager-fitness` | `TickBoundarySeededTest` baseline | The `Arrivals` entry is removed from the committed baseline when the reset path no longer joins on the tick. The rule itself is unchanged. |

## Hold semantics

While pending, the racer is held with the same mechanism the spawn freeze uses (`Racers` hold, no gliding). `CupSession` skips the
racer's advance and ring progress on held ticks, so no ring is credited and no effect applied, as the new requirement states.

## Fitness

The existing `noBlockingOnFuturesOnTheTick` rule (`TickBoundarySeededTest.java:41-44`) covers the new class. No new rule is added;
the change shrinks the frozen baseline and the rule keeps failing any new blocking wait in race or platform.

## Decisions

No ADR. The change applies the non-blocking arrival rule already recorded for map moves. The measurement task records the stall
before and after, in the code pull request, so the owner can confirm the hold length (proposal open question).
