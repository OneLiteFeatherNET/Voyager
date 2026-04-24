---
name: ecs-system-registration
description: Canonical registration order and wiring points for ECS systems in the server module
type: project
---

Server-module ECS systems are registered in `GameOrchestrator.startGame()`. The `EntityManager` executes systems in registration order each tick. The current order (as of 2026-04-23) is:

1. `ElytraPhysicsSystem` — flight integration, always first so later systems see fresh velocity/position
2. `FireworkBoostSystem` — input-driven impulse, runs before collision so boost-this-tick still counts toward collisions
3. `RingCollisionSystem`
4. `OutOfBoundsSystem`
5. `RingEffectSystem`
6. `RingVisualizationSystem`
7. `SplineVisualizationSystem`
8. `ScoreDisplaySystem`

Other wiring points:

- `GameEntityFactory.createPlayerEntity` — attach per-player components here. Standard set as of 2026-04-23: `PlayerRefComponent`, `ElytraFlightComponent`, `FireworkBoostComponent`, `HudComponent`, `RingTrackerComponent`, `ScoreComponent`, `RingEffectComponent`.
- `GameEntityFactory.createGameEntity` — attach per-game components here
- `GameOrchestrator.loadNextMap` — iterate entities and push per-map config onto components (pattern: check `entity.getComponent(X.class) != null` before setting). Also used to fan out per-map HUD calls (`hud.showMapTitle`, `hud.showCupProgress`).
- `GameOrchestrator.restartGame` / `cleanup` — iterate entities and call `hud.cleanup()` before removing player entities so boss bars are hidden.
- Components mutated from both Netty and tick threads use `AtomicBoolean` / `volatile` (see `FireworkBoostComponent`). Components written only from the tick thread (e.g. `HudComponent`) need no synchronisation.

**Why:** The order enforces a physics → input → collision → bounds → visuals pipeline. Moving a system breaks assumptions in later systems (for example, collision assumes velocity is up-to-date).

**How to apply:** When documenting or reviewing a new system, place it in the pipeline by reading/writing relationships, not by filename. Always cross-reference these three files together: the component class, the system class, and the orchestrator registration block.
