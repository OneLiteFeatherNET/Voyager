# ADR-0009: Split HudComponent into data and HudRenderer strategy

## Status

Accepted

## Date

2026-04-25

## Decision makers

- Voyager server team

## Context and problem statement

`HudComponent` currently holds BossBar handles and a `Player` reference and also owns the rendering methods (`renderRingPassed`, `renderScoreUpdate`, `renderMedalScreen`). Race Mode and Practice Mode (see [ADR-0004](0004-practice-mode-replaces-tutorial.md)) need different HUD layouts:

- Race Mode shows position, total score, and a per-cup point progression bar.
- Practice Mode shows the current attempt time, the player's personal best, and the medal threshold thresholds for the active lesson.

Adding mode-specific rendering inside the existing `HudComponent` would force every render method to grow a `if (mode == ...)` branch. The component would also continue to mix data ownership with rendering behaviour, violating Single Responsibility.

ECS components are looked up by class — `entity.getComponent(HudComponent.class)` — so subclassing `HudComponent` is not a viable extension point.

## Decision drivers

- Single Responsibility: a component holds data, a system or strategy holds behaviour
- ECS lookup by class: components cannot be polymorphically substituted by subclass
- "Strategy over if/else": mode-specific behaviour belongs in a strategy, not in branches
- ManisGame Rule 1: sealed interface plus `Base*` non-sealed abstract for controlled extension
- The user approved doing the refactor now rather than deferring it

## Considered options

- Option A: Add a `mode` field to `HudComponent` and branch inside each render method
- Option B: Create `RaceHudComponent` and `PracticeHudComponent` as subclasses of `HudComponent`
- Option C: Make `HudComponent` data-only and move rendering into a sealed `HudRenderer` strategy

## Decision outcome

Chosen option: **Option C — data-only `HudComponent` plus sealed `HudRenderer` strategy**, because it separates data from rendering, keeps ECS class-based lookup intact, and follows the Strategy pattern already used for scoring (see [ADR-0007](0007-scoring-strategy-pattern.md)).

`HudComponent` keeps the BossBar handles and the `Player` reference and gains nothing else. `HudRenderer` is a `sealed` interface that `permits BaseHudRenderer`. `RaceHudRenderer` and `PracticeHudRenderer` are `final` implementations.

`RingCollisionSystem` and `ScoreDisplaySystem` receive a `HudRenderer` via constructor injection. They now call `renderer.renderRingPassed(hud, ring, score)` instead of `hud.renderRingPassed(ring, score)`. The systems remain mode-agnostic; only the renderer instance changes per session.

The renderer is selected at session construction by a `HudRendererFactory.forMode(GameMode)` that mirrors the scoring factory pattern.

### Consequences

- Good, because `HudComponent` becomes a pure data record and is trivially testable.
- Good, because adding a future mode is one new final renderer class plus one factory branch.
- Good, because ECS lookup `entity.getComponent(HudComponent.class)` continues to work for both modes.
- Good, because mode-specific rendering does not pollute systems with branches.
- Bad, because every system that previously called methods on `HudComponent` directly must be updated to call the renderer instead. Two systems are affected (`RingCollisionSystem`, `ScoreDisplaySystem`).
- Neutral, because the renderer is a stateless final class shared across all entities of a session — no per-entity allocation cost.

### Confirmation

- `HudComponent` declares only fields and a compact constructor — no render methods.
- `HudRenderer` is `sealed` and `permits BaseHudRenderer`.
- `BaseHudRenderer` is `non-sealed abstract`.
- `RaceHudRenderer` and `PracticeHudRenderer` are `final`.
- `RingCollisionSystem` and `ScoreDisplaySystem` accept a `HudRenderer` in their constructor.
- `HudRendererFactory` is `abstract` with a `private` constructor and is annotated `@ApiStatus.Internal`.
- No production code calls a `render*` method on `HudComponent`.

## Pros and cons of the options

### Option A — HudComponent with mode field and branches

- Good, because no new types are needed.
- Bad, because every render method grows an `if (mode == ...)` block.
- Bad, because data and behaviour stay coupled in one class.
- Bad, because adding a mode requires editing every existing render method.

### Option B — RaceHudComponent and PracticeHudComponent subclasses

- Good, because rendering behaviour lives on the right type.
- Bad, because `entity.getComponent(HudComponent.class)` returns the base type — callers must downcast or maintain parallel lookup keys.
- Bad, because two component classes for the same data shape duplicates the BossBar plumbing.
- Bad, because ECS systems would have to know which subclass to look up, undermining the class-based lookup contract.

### Option C — data-only HudComponent plus sealed HudRenderer

- Good, because data and behaviour are separated cleanly.
- Good, because ECS lookup stays unchanged.
- Good, because the strategy pattern is consistent with scoring (ADR-0007).
- Bad, because two systems must be updated to take a renderer in their constructor.

## More information

- Related: [ADR-0004: Practice Mode replaces Tutorial Mode](0004-practice-mode-replaces-tutorial.md)
- Related: [ADR-0006: GameMode enum in shared/common](0006-gamemode-enum-in-shared-common.md)
- Related: [ADR-0007: Scoring uses the Strategy pattern](0007-scoring-strategy-pattern.md)
- ManisGame design rules: CLAUDE.md, section "Design Reference Rules", Rules 1, 2, and 7
