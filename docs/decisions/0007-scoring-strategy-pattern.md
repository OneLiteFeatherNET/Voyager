# ADR-0007: Replace ScoringService with the ScoringStrategy pattern

## Status

Accepted. Removes `ScoringService` and `ScoringServiceImpl` introduced before [ADR-0005](0005-gamemode-enum-session-discriminator.md).

## Date

2026-04-25

## Decision makers

- Voyager server team

## Context and problem statement

`ScoringService` and `ScoringServiceImpl` previously held the per-map and per-cup scoring logic for Race Mode. With Practice Mode added in [ADR-0004](0004-practice-mode-replaces-tutorial.md), scoring diverges by mode:

- Race Mode awards bracket points (see [ADR-0003](0003-race-mode-time-bracket-scoring.md)) plus position bonus plus ring points.
- Practice Mode awards no position points and no leaderboard rank, but it computes a per-attempt medal tier from the elapsed time and updates the player's personal best for the lesson.

A single implementation with `if (mode == PRACTICE)` branches violates the project convention "Strategy over if/else" and the Open/Closed principle. Both modes still share `MedalTier` and `MedalBrackets`, which already live in `shared/common`.

## Decision drivers

- Open/Closed: adding a future mode must not require editing existing scoring code
- ManisGame Rule 1: sealed interface plus `Base*` non-sealed abstract class for controlled extension
- ManisGame Rule 7: factories injectable via a `@FunctionalInterface` Creator so tests can substitute implementations
- DRY for medal tier and bracket logic — one definition in `shared/common`

## Considered options

- Option A: Keep `ScoringService` and add a `mode` field plus switch branches inside `ScoringServiceImpl`
- Option B: Replace `ScoringService` with a sealed `ScoringStrategy` interface, two final implementations, and a `ScoringStrategyFactory`
- Option C: Keep two separate `ScoringService` classes that share the interface but without sealing

## Decision outcome

Chosen option: **Option B — sealed `ScoringStrategy` plus mode-specific final implementations**, because it follows the project's established pattern (ManisGame Rules 1 and 7), removes branching at call sites, and makes the closed set of strategies explicit in the type system.

`ScoringService` and `ScoringServiceImpl` are deleted (clean break, user-approved). Call sites that previously held a `ScoringService` reference now hold a `ScoringStrategy` reference, selected at session construction by `ScoringStrategyFactory.forMode(GameMode)`.

The new contract method is `onMapCompleted(UUID playerId, Duration elapsed, MapDefinition map)`, which both strategies implement. `RaceScoringStrategy` returns a `RaceMapResult` (bracket, points, position bonus); `PracticeScoringStrategy` returns a `PracticeMapResult` (medal tier, new personal best flag).

Both strategies share `MedalTier` and `MedalBrackets` from `shared/common`. They differ in how they interpret the bracket: Race Mode maps the bracket to point values; Practice Mode maps the bracket to a medal tier and persists the personal best.

### Consequences

- Good, because adding a future mode is one new final class plus one factory branch — no existing strategy code changes.
- Good, because each strategy is independently unit-testable with its own fake clock and fake repository.
- Good, because the sealed interface communicates the closed set at compile time.
- Bad, because callers must now type-narrow when they need mode-specific result data (`RaceMapResult` vs `PracticeMapResult`). Pattern matching on sealed result types keeps this readable.
- Bad, because `ScoringService` removal is a breaking change inside `server/`. No external module depended on it.
- Neutral, because total line count is roughly the same — branches move from one file to two.

### Confirmation

- `server/src/main/java/net/elytrarace/server/scoring/ScoringStrategy.java` is `sealed` and `permits BaseScoringStrategy`.
- `BaseScoringStrategy` is `non-sealed abstract`.
- `RaceScoringStrategy` and `PracticeScoringStrategy` are `final`.
- `ScoringStrategyFactory` is `abstract` with a `private` constructor and is annotated `@ApiStatus.Internal`.
- The factory exposes a `@FunctionalInterface ScoringStrategyCreator` per ManisGame Rule 7.
- `ScoringService` and `ScoringServiceImpl` no longer exist under `server/src/main/java/`.
- `MedalTier` and `MedalBrackets` are referenced from `shared/common`, not duplicated in `server/`.

## Pros and cons of the options

### Option A — `ScoringServiceImpl` with mode branches

- Good, because no new types are introduced.
- Bad, because every method grows an `if (mode == ...)` block.
- Bad, because adding a future mode forces edits across the entire file.
- Bad, because it violates the project convention "Strategy over if/else".

### Option B — sealed ScoringStrategy plus final implementations

- Good, because it matches ManisGame Rule 1 (sealed interface plus `Base*` non-sealed abstract).
- Good, because each mode owns its scoring code in isolation.
- Good, because the factory is injectable for tests per Rule 7.
- Bad, because callers narrow on the result type when they need mode-specific fields.

### Option C — two ScoringService classes without sealing

- Good, because the call site keeps a single interface type.
- Bad, because the closed set of implementations is not communicated at compile time.
- Bad, because future contributors can add a third implementation without going through `ScoringStrategyFactory`, bypassing the selection point.

## More information

- Related: [ADR-0003: Race Mode bracket scoring](0003-race-mode-time-bracket-scoring.md)
- Related: [ADR-0004: Practice Mode replaces Tutorial Mode](0004-practice-mode-replaces-tutorial.md)
- Related: [ADR-0005: GameMode enum session discriminator](0005-gamemode-enum-session-discriminator.md)
- Related: [ADR-0006: GameMode enum in shared/common](0006-gamemode-enum-in-shared-common.md)
- Related: [ADR-0010: Single game_results table with mode discriminator](0010-game-results-mode-discriminator.md)
- ManisGame design rules: CLAUDE.md, section "Design Reference Rules", Rules 1, 2, and 7
