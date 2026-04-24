# ADR-0002: Move HUD state into an ECS component

## Status

Accepted

## Date

2026-04-23

## Decision makers

- Voyager server team

## Context and problem statement

HUD state for a player (cup-progress boss bar, actionbar text, title overlays, ring-pass feedback) previously lived in `GameHud` instances owned by a `GameHudManager`. The manager held a `ConcurrentHashMap<UUID, GameHud>` and exposed bulk operations such as `addPlayer`, `removePlayer`, `showCupProgressAll`, and `showMapTitleAll`. `GameOrchestrator` held a `GameHudManager` field and called it directly. `RingCollisionSystem` received the manager through its constructor so it could send ring-pass feedback.

This placed per-player HUD state outside the entity graph and duplicated the UUID-to-player lookup that `EntityManager` already provides. `ScoreDisplaySystem` sidestepped the manager entirely and called `player.sendActionBar()` itself, which duplicated the formatting logic that `GameHud.updateActionbar()` already implemented.

## Decision drivers

- Per-player state lives on the entity, not in ad-hoc maps keyed by UUID
- Systems depend on components, not on auxiliary service classes
- One formatting implementation per HUD element
- The tick thread owns all per-player gameplay state, so no `ConcurrentHashMap` is needed for state that is only written from the tick thread

## Considered options

- Option A: Keep `GameHud` and `GameHudManager` and pass the manager into every system that needs HUD access
- Option B: Move HUD state onto a new `HudComponent` attached to each player entity
- Option C: Extend `PlayerRefComponent` with HUD methods

## Decision outcome

Chosen option: **Option B — dedicated `HudComponent` attached to every player entity**, because it matches the system-per-behaviour and component-per-state pattern used elsewhere in the server module (`FireworkBoostComponent`, `ScoreComponent`, `RingTrackerComponent`).

`GameEntityFactory.createPlayerEntity()` attaches a `HudComponent` to every player entity. `ScoreDisplaySystem` declares `HudComponent` in `getRequiredComponents()` and routes the actionbar update through `HudComponent.updateActionbar()`. `RingCollisionSystem` no longer receives a manager through its constructor — it reads `HudComponent` directly from the entity it is processing. `GameOrchestrator.loadNextMap()` iterates entities and calls `hud.showMapTitle()` and `hud.showCupProgress()` per player. `GameOrchestrator.cleanup()` and `restartGame()` iterate entities and call `hud.cleanup()` before removing player entities.

### Consequences

- Good, because HUD state is tied to the entity lifecycle. Removing a player entity in `restartGame()` triggers `hud.cleanup()`, so the boss bar is hidden without a separate manager call.
- Good, because `RingCollisionSystem` now has a single constructor `RingCollisionSystem(EntityManager)` and no non-ECS dependency.
- Good, because `ScoreDisplaySystem` no longer duplicates actionbar formatting. One implementation lives on `HudComponent.updateActionbar()`.
- Good, because `HudComponent` holds no concurrent data structure. The tick thread is the sole writer, so the `ConcurrentHashMap` from `GameHudManager` is gone.
- Bad, because `GameHud` and `GameHudManager` in `server/src/main/java/net/elytrarace/server/ui/` are now dead code pending deletion in a follow-up cleanup.
- Neutral, because per-entity access replaces bulk operations. Code that previously called `hudManager.showMapTitleAll(name)` now iterates entities with a `HudComponent`. The iteration cost is one method call per player per map transition.

### Confirmation

- `GameOrchestrator` contains no `GameHudManager` field, no `getHudManager()` getter, and no call to `hudManager.addPlayer()`.
- `RingCollisionSystem` has a single constructor `RingCollisionSystem(EntityManager entityManager)` and reads `HudComponent` via `entity.getComponent(HudComponent.class)` inside `process()`.
- `ScoreDisplaySystem.getRequiredComponents()` includes `HudComponent.class`, and the system calls `hud.updateActionbar(...)` instead of `player.sendActionBar(...)`.
- `GameEntityFactory.createPlayerEntity()` adds `new HudComponent(player)` to every player entity.
- No production code imports `net.elytrarace.server.ui.GameHud` or `net.elytrarace.server.ui.GameHudManager`.

## Pros and cons of the options

### Option A — keep `GameHud` and `GameHudManager`

- Good, because no code changes are required.
- Bad, because per-player state lives outside the entity graph in a parallel registry.
- Bad, because `RingCollisionSystem` depends on a non-ECS service class.
- Bad, because `ScoreDisplaySystem` still has no sanctioned way to reach the HUD and keeps duplicating formatting.

### Option B — HUD as an ECS component

- Good, because HUD state is owned by the entity and cleaned up through the normal entity-removal path.
- Good, because systems declare HUD access through `getRequiredComponents()`, which makes the dependency explicit.
- Good, because removing `GameHudManager` removes a `ConcurrentHashMap` that existed only because state lived outside the tick thread.
- Bad, because `GameHud` and `GameHudManager` become dead code until a cleanup commit removes them.

### Option C — extend `PlayerRefComponent` with HUD methods

- Good, because no new component class is needed.
- Bad, because `PlayerRefComponent` exists to expose the `Player` reference. Adding HUD methods conflates identity with presentation.
- Bad, because systems that only need the `Player` reference would pull in HUD concerns, breaking the single-responsibility pattern used by other components.

## More information

- Component: `server/src/main/java/net/elytrarace/server/ecs/component/HudComponent.java`
- Factory: `server/src/main/java/net/elytrarace/server/ecs/GameEntityFactory.java`
- Orchestrator: `server/src/main/java/net/elytrarace/server/game/GameOrchestrator.java`
- Systems: `server/src/main/java/net/elytrarace/server/ecs/system/RingCollisionSystem.java`, `server/src/main/java/net/elytrarace/server/ecs/system/ScoreDisplaySystem.java`
- Dead code pending deletion: `server/src/main/java/net/elytrarace/server/ui/GameHud.java`, `server/src/main/java/net/elytrarace/server/ui/GameHudManager.java`
- Related reference: [HUD component](../reference/hud.md)
- Related how-to: [Register an ECS system](../guides/how-to-register-an-ecs-system.md)
- Related ADR: [ADR-0001: Move firework boost logic into the ECS](0001-firework-boost-in-ecs.md)
