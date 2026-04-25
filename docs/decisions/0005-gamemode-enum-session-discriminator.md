# ADR-0005: GameMode enum discriminates session behavior

## Status

Accepted

## Date

2026-04-25

## Decision makers

- Voyager server team
- Voyager architecture review

## Context and problem statement

Voyager ships two gameplay modes: Race Mode (cup-based competitive racing with time brackets) and Practice Mode (repeatable skill lessons with medal tiers). The two modes share the same elytra physics, ring collision, ECS systems, and HUD framework, but they diverge in phase flow, scoring, HUD layout, and what gets persisted at the end of a session.

The server module needs a way to express "this session is Race" or "this session is Practice" so that phases, scoring services, HUD components, and persistence behave correctly without growing parallel code paths or implicit configuration coupling.

## Decision drivers

- The MVP runs one game session per server process; concurrent sessions live in separate CloudNet processes, not within one JVM
- Mode-dependent behavior is concentrated in a small set of components: phases, scoring, HUD, persistence
- The two-mode set is fixed for the foreseeable scope; a third mode is not planned
- The mode must be readable from the tick thread without locks
- Adding a mode must not require restructuring the session lifecycle or the orchestrator

## Considered options

- Option A: Add a `GameMode` enum stored on `GameSession` and read by phases, scoring, HUD, and persistence
- Option B: Ship separate server binaries per mode
- Option C: Use feature flags in configuration to toggle Race or Practice behavior
- Option D: Introduce polymorphic `GameSession` subclasses (`RaceGameSession`, `PracticeGameSession`)

## Decision outcome

Chosen option: **Option A — `GameMode` enum on `GameSession`**, because the mode is a small, closed set of values that toggles behavior in a small, named set of consumers. A class hierarchy is over-engineered for two values, and separate binaries or feature flags hide the discriminator from the type system.

The enum lives in the server module and declares two values:

```java
public enum GameMode {
    RACE,
    PRACTICE
}
```

`GameSession` exposes the mode as an immutable field set at session construction. The following consumers read it:

| Consumer | Reads `GameMode` to decide |
|---|---|
| `GameOrchestrator` | Which phase series to install (`Lobby -> Preparation -> Race -> End` or `Lobby -> Preparation -> Practice -> End`) |
| Phase implementations | Which scoring service and HUD layout to bind during phase start |
| `ScoringService` | Whether to apply bracket scoring (Race) or medal tiers per lesson (Practice) |
| `HudComponent` | Which HUD layout to render |
| Persistence layer | Which result row to write (cup standings or per-lesson personal best) |

The mode is set once at session construction and never mutated. All reads happen on the tick thread, so the field needs no synchronization beyond `final`.

### Consequences

- Good, because adding a mode-aware behavior is a single `switch` expression in one consumer, not a cross-module refactor
- Good, because the type system surfaces every site that branches on mode, which makes adding a third mode straightforward to audit
- Good, because the change is additive: existing sessions become `GameMode.RACE` and behave as before
- Bad, because the `switch` sites are scattered across phases, scoring, HUD, and persistence, so adding a third mode means editing each site
- Bad, because the enum itself does not enforce that every consumer handles every value; reviewers must check that `switch` expressions are exhaustive
- Neutral, because the MVP one-session-per-process constraint is unchanged; the enum does not introduce concurrent sessions

### Confirmation

- A `GameMode` enum exists in the server module with values `RACE` and `PRACTICE`
- `GameSession` declares `private final GameMode mode` set in the constructor and exposes it through a getter
- `GameOrchestrator` selects the phase series from `session.mode()` at session start
- `ScoringService`, `HudComponent`, and the persistence layer each branch on `session.mode()` with exhaustive `switch` expressions
- Unit tests cover both mode paths in each consumer

## Pros and cons of the options

### Option A: `GameMode` enum on `GameSession`

- Good, because minimal invasive change; the existing session model gains one field
- Good, because the type system makes every branch site grep-able
- Bad, because branch sites are scattered across consumers

### Option B: Separate server binaries per mode

- Good, because each binary is single-purpose and has no mode branching
- Bad, because doubles deployment overhead: two artifacts, two CloudNet task definitions, two release pipelines
- Bad, because shared infrastructure code (lobby, queue, end screen) must live in a third artifact or be duplicated

### Option C: Feature flags in configuration

- Good, because no source change needed beyond reading config
- Bad, because the discriminator becomes implicit; a reader of the code cannot tell which mode a code path serves without inspecting the runtime config
- Bad, because tests must construct configurations to exercise each mode, instead of constructing a session with a typed enum

### Option D: Polymorphic `GameSession` subclasses

- Good, because each subclass owns its mode-specific behavior in one place
- Bad, because two subclasses for two modes is the textbook over-engineering of class hierarchies
- Bad, because most session behavior is shared, so the subclasses would mostly delegate to the base class
- Bad, because phase, scoring, HUD, and persistence consumers would need to either downcast or accept the base type and lose mode information

## More information

- The Race Mode scoring path that this enum routes to is defined in [ADR-0003: Race Mode uses fixed time-bracket scoring per map](0003-race-mode-time-bracket-scoring.md)
- The Practice Mode scoring and lesson model that this enum routes to is defined in [ADR-0004: Tutorial Mode is redesigned as Practice Mode](0004-practice-mode-replaces-tutorial.md)
- Concurrent sessions remain a CloudNet-process-level concern; the in-JVM session model stays single-session for the MVP
