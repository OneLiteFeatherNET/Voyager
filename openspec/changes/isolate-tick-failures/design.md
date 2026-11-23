# Design

## Dependency direction

`voyager-server` is the composition root and may name every module. The new classes live there, next to `VoyagerServer`, and
reach `voyager-platform` (the cup session, the waiting room) only through the types `scheduleTick` already receives. No
domain or use-case module changes.

```
VoyagerServer (composition root)
  -> TickGuard            (new, voyager-server)  holds the per-cup "stopped" state
       -> TickSteps       (functional interfaces: cup, lobby, restart, statistics)
```

`TickGuard` takes its steps as functional interfaces. `VoyagerServer` passes the real ones; a test passes lambdas that throw.
No Minestom type appears in `TickGuard`.

## Types

| Type | Module | Purpose |
|---|---|---|
| `TickGuard` | `voyager-server` (`net.elytrarace.voyager.server`) | Runs the cup step in a `try`, sets `stopped` on the first failure, runs the other steps outside it. |
| `TickSteps` | same | The four steps as `@FunctionalInterface`s, so the guard can be built without Minestom. |

`TickGuard` is package-private and carries no DI annotation; `VoyagerServer` creates it by hand, as it does today.

## Boundary rules

- `TickGuard` imports no Minestom type and no platform type; its only inputs are the `TickSteps` functions. Whether the existing
  `voyager-fitness` rules already cover `voyager-server` for this is to be checked when the code lands. If no rule names the
  package, a rule is added to `voyager-fitness` in the same change, and `FitnessCoverageTest` keeps its entry.

## Tick semantics

The task scheduled by `scheduleTick` keeps its `ExecutionType.TICK_START` and its one-tick period. Inside it:

1. If the cup is not stopped, run the cup step in a `try`.
2. Catch `RuntimeException`, stop the cup, log once with the cup's state.
3. Run the waiting room, the restart hand-off and the statistics replies outside that `try`, each in its own `try` that logs and
   continues, so a throw in the lobby does not stop the statistics replies.

The order is kept: cup first, then waiting room, restart, statistics, as the comments at `VoyagerServer.java:600-606` require.

## Round reset

`CupSession` exposes no reset today. The round reset clears `stopped` in `TickGuard` and calls the cup's validation before its
first tick. Validation reuses the existing cup-validation checks (`openspec/specs/cup-validation`); it adds no new rule.

## Decisions

No ADR is proposed: the change applies the existing "fail-fast per cup" practice and does not alter a design decision. Scribe
records it in `docs/` when the code lands (see tasks 4.x).
