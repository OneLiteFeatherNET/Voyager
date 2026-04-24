# ADR-0001: Move firework boost logic into the ECS

## Status

Accepted

## Date

2026-04-23

## Decision makers

- Voyager server team

## Context and problem statement

The firework boost previously ran entirely inside `PlayerEventHandler`, a Minestom event listener. The listener owned a `ConcurrentHashMap<UUID, Long>` for per-player cooldowns, computed the look-direction impulse, and called `player.setVelocity()` directly from the Netty I/O thread. Per-map `BoostConfig` was pushed onto the handler from `GameOrchestrator.loadNextMap()`.

This placed per-tick game logic and mutable player state outside the tick loop and outside the entity graph. The server module treats the ECS as the single authority for per-tick gameplay, so the event-handler implementation broke that contract.

## Decision drivers

- Systems own per-tick game logic; event handlers signal intent
- One thread (the tick thread) owns player gameplay state; Netty only signals into it
- Per-player state lives on the entity, not in ad-hoc maps keyed by UUID
- Boost behaviour must be unit-testable without booting a Minestom server

## Considered options

- Option A: Keep the boost in `PlayerEventHandler` and pass the tick thread a handle
- Option B: Move the boost into a dedicated ECS system backed by a new component
- Option C: Fold the boost into `ElytraPhysicsSystem`

## Decision outcome

Chosen option: **Option B — dedicated `FireworkBoostSystem` and `FireworkBoostComponent`**, because it matches the existing system-per-behaviour pattern (`ElytraPhysicsSystem`, `RingCollisionSystem`, `OutOfBoundsSystem`) and cleanly separates the Netty signal from the tick-thread action.

`PlayerEventHandler.onUseItem` now only looks up the player entity and calls `boostComp.requestBoost()`. `FireworkBoostSystem` runs each tick after `ElytraPhysicsSystem`, ticks the cooldown counter, claims pending requests atomically, applies the impulse, and sends the `SetCooldownPacket`. `GameOrchestrator.loadNextMap()` iterates the entity graph and updates each player's `FireworkBoostComponent.boostConfig`.

### Consequences

- Good, because cooldown state and boost math live on the entity, so they survive alongside other per-player components and are reset when the entity is removed during a restart.
- Good, because the tick thread owns the impulse application. The Netty thread only flips an `AtomicBoolean`, which removes the cross-thread `setVelocity()` call.
- Good, because `FireworkBoostSystem` can be unit-tested with a fake entity and a stubbed `Player`, without any Minestom event machinery.
- Bad, because the impulse lands one tick after the key press instead of synchronously in the event handler. The maximum added latency is 50 ms at 20 TPS.
- Neutral, because the system runs for every entity that has the three required components every tick, even when no boost is pending. The cost is one integer decrement and one `compareAndSet` per player per tick.

### Confirmation

- `PlayerEventHandler` contains no `ConcurrentHashMap`, no `applyBoost()` method, and no `setBoostConfig()` method.
- `GameOrchestrator.startGame()` registers `FireworkBoostSystem` after `ElytraPhysicsSystem`.
- `GameEntityFactory.createPlayerEntity()` attaches a `FireworkBoostComponent` to every player entity.
- `GameOrchestrator.loadNextMap()` pushes the new `BoostConfig` by iterating entities, not by calling the event handler.

## Pros and cons of the options

### Option A — keep the boost in `PlayerEventHandler`

- Good, because the impulse is applied in the same tick as the key press.
- Bad, because gameplay state lives outside the entity graph in a `ConcurrentHashMap`.
- Bad, because `player.setVelocity()` runs from the Netty thread.
- Bad, because the behaviour is hard to test in isolation.

### Option B — dedicated ECS system and component

- Good, because it matches the system-per-behaviour pattern used elsewhere in the server module.
- Good, because thread ownership is explicit: Netty writes, the tick thread reads.
- Good, because the cooldown counter is a tick count, not a wall-clock timestamp, so it tracks game time instead of real time.
- Bad, because the boost is delayed by up to one tick (50 ms at 20 TPS).

### Option C — fold the boost into `ElytraPhysicsSystem`

- Good, because it avoids adding a new system class.
- Bad, because it conflates flight integration with discrete input handling, which makes both harder to reason about and test.
- Bad, because `ElytraPhysicsSystem` would have to own the cooldown state, breaking its single responsibility.

## More information

- Component: `server/src/main/java/net/elytrarace/server/ecs/component/FireworkBoostComponent.java`
- System: `server/src/main/java/net/elytrarace/server/ecs/system/FireworkBoostSystem.java`
- Event handler: `server/src/main/java/net/elytrarace/server/player/PlayerEventHandler.java`
- Orchestrator: `server/src/main/java/net/elytrarace/server/game/GameOrchestrator.java`
- Related reference: [Firework boost system](../reference/firework-boost.md)
- Related how-to: [Register an ECS system](../guides/how-to-register-an-ecs-system.md)
