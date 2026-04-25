# ADR-0008: Use CyclicPhaseSeries for Practice Mode retries

## Status

Accepted

## Date

2026-04-25

## Decision makers

- Voyager server team

## Context and problem statement

Practice Mode (see [ADR-0004](0004-practice-mode-replaces-tutorial.md)) auto-loops a lesson on completion: the player crosses the finish ring, the medal screen shows for a short window, and the lesson restarts at the start ring with a fresh ring tracker. The user approved the auto-loop flow over a manual `/retry` command.

The phase pipeline currently uses Xerus's `LinearPhaseSeries`, which advances through phases once and ends. Race Mode keeps that flow. Practice Mode needs the same lifecycle for intro and outro, but the gameplay phase plus a short reset phase must repeat until the player leaves.

Three implementation paths were considered. None of them fit cleanly inside `LinearPhaseSeries` without breaking the phase contract or leaking allocations.

## Decision drivers

- Practice retries must be allocation-stable: a player who runs a lesson 50 times must not produce 50 sets of phase objects
- Race Mode must keep its current `LinearPhaseSeries` behaviour with no regressions
- The Xerus phase contract assumes forward-only transitions; backward jumps are not part of the API
- Rule of Three: do not generalize a construct into `shared/` until a third consumer appears

## Considered options

- Option A: Recreate `LinearPhaseSeries` on every retry with fresh `Phase` instances
- Option B: Add a `RetryPhase` that reaches back into the active `LinearPhaseSeries` and resets the cursor
- Option C: Introduce `CyclicPhaseSeries` in the `server` module, alongside `LinearPhaseSeries`
- Option D: Resurrect `shared/phase` and place `CyclicPhaseSeries` there for cross-module reuse

## Decision outcome

Chosen option: **Option C — `CyclicPhaseSeries` in `server/src/main/java/net/elytrarace/server/phase/`**, because it adds the cyclic semantics in one place, reuses the existing `Phase` instances across retries, and keeps the construct local to its only consumer until a second one appears.

`CyclicPhaseSeries` accepts three phase lists at construction:

- `intro` — runs once on series start (Practice composition: `[LobbyPhase]`)
- `cycle` — runs repeatedly until the cycle is broken (Practice composition: `[PracticeGamePhase, RetryPhase]`)
- `outro` — runs once when the cycle is broken (Practice composition: `[EndPhase]`)

The cycle is broken by an explicit `breakCycle()` call on the series, triggered when the player leaves the lesson or the session ends. After the medal screen, `RetryPhase` runs as a one-tick reset that teleports the player to the start ring and clears the ring tracker, then yields control back to the series, which re-enters `PracticeGamePhase`.

Race Mode continues to use `LinearPhaseSeries` unchanged.

### Consequences

- Good, because phase objects are allocated once per session and reused across every retry.
- Good, because the cyclic contract is explicit at the series level — phases stay forward-only and unaware of looping.
- Good, because Race Mode is unaffected; `LinearPhaseSeries` keeps its current behaviour and tests.
- Good, because keeping the class in `server/` honours Rule of Three; it can be promoted to `shared/` if a second consumer appears.
- Bad, because the `server` module now owns two phase-series implementations. The shared lifecycle code (start, finish, error handling) is small enough that the duplication is acceptable today.
- Neutral, because `RetryPhase` is a one-tick phase. Its only side effects are the teleport and the ring tracker reset.

### Confirmation

- `server/src/main/java/net/elytrarace/server/phase/CyclicPhaseSeries.java` exists.
- The Practice session builder constructs the series with `intro=[LobbyPhase]`, `cycle=[PracticeGamePhase, RetryPhase]`, `outro=[EndPhase]`.
- `RetryPhase.start()` teleports the player to the lesson start ring and resets the ring tracker; `RetryPhase.tick()` finishes after one tick.
- The Race session builder still uses `LinearPhaseSeries`.
- No file under `shared/` references `CyclicPhaseSeries`.
- No `Phase` instance is recreated between retries within a single Practice session.

## Pros and cons of the options

### Option A — recreate LinearPhaseSeries on every retry

- Good, because no new series type is needed.
- Bad, because every retry allocates a fresh set of phase objects.
- Bad, because phase-local state (timers, listeners) must be torn down and rebuilt every retry, which is a leak risk.
- Bad, because the orchestrator must own the retry loop, pulling lifecycle logic out of the series.

### Option B — RetryPhase that reaches into LinearPhaseSeries

- Good, because it keeps a single series type.
- Bad, because it requires a backward transition that `LinearPhaseSeries` was never designed for.
- Bad, because `RetryPhase` would have to know the index of `PracticeGamePhase` inside the series, coupling phase to its container.
- Bad, because forward-only transitions are part of the Xerus contract, not just an implementation detail.

### Option C — CyclicPhaseSeries in server/

- Good, because the cyclic semantics are explicit at the series level.
- Good, because phase objects are reused across retries.
- Good, because Race Mode is untouched.
- Bad, because the `server` module owns two series implementations. Acceptable until a second consumer of cycling appears.

### Option D — resurrect shared/phase for CyclicPhaseSeries

- Good, because it would centralise both series implementations.
- Bad, because there is exactly one consumer today (Practice in `server/`). Rule of Three says wait for the third.
- Bad, because resurrecting `shared/phase` reintroduces a module that was deliberately removed in the recent refactor.
- Bad, because premature generalization tends to lock in API choices before the second use case clarifies what is actually shared.

## More information

- Related: [ADR-0004: Practice Mode replaces Tutorial Mode](0004-practice-mode-replaces-tutorial.md)
- Related: [ADR-0005: GameMode enum session discriminator](0005-gamemode-enum-session-discriminator.md)
- Related: [ADR-0007: Scoring uses the Strategy pattern](0007-scoring-strategy-pattern.md)
- Xerus phase API: `net.theevilreaper.xerus.api.phase.*`
