# Firework boost system

This reference documents the component, system, and configuration record that implement the firework-rocket boost on the Minestom game server.

## FireworkBoostComponent

Holds per-player boost intent, cooldown state, and the active `BoostConfig`. Attached to every player entity by `GameEntityFactory.createPlayerEntity`.

Fully qualified name: `net.elytrarace.server.ecs.component.FireworkBoostComponent`

### Fields

| Field | Type | Description |
|---|---|---|
| `boostRequested` | `AtomicBoolean` | Set from the Netty thread when the player uses a firework rocket. Cleared atomically by the tick thread. |
| `cooldownRemainingTicks` | `int` | Game ticks until the next boost is allowed. Decremented every tick by `FireworkBoostSystem`. |
| `boostConfig` | `volatile BoostConfig` | Active per-map boost configuration. Defaults to `BoostConfig.DEFAULT`. |

### Methods

#### `requestBoost()`

Signals that the player pressed the boost button. Safe to call from any thread.

**Returns:** `void`

#### `claimBoostRequest()`

Atomically reads and clears the boost-request flag.

**Returns:** `boolean` — `true` if a boost was pending and has now been consumed, `false` otherwise

#### `tickCooldown()`

Decrements the cooldown counter by one tick. No-op when the counter is already zero. Called every tick by `FireworkBoostSystem`.

**Returns:** `void`

#### `isOnCooldown()`

**Returns:** `boolean` — `true` while the cooldown has not yet expired

#### `startCooldown()`

Resets the cooldown counter to `boostConfig.cooldownMs() / 50`. Call this immediately after applying a boost.

**Returns:** `void`

#### `getCooldownRemainingTicks()`

**Returns:** `int` — the number of ticks remaining on the cooldown

#### `getBoostConfig()`

**Returns:** `BoostConfig` — the active boost configuration

#### `setBoostConfig(BoostConfig)`

Updates the boost configuration. Safe to call from any thread. Invoked by `GameOrchestrator.loadNextMap()` for every player entity when a new map is loaded.

**Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `boostConfig` | `BoostConfig` | The new configuration |

**Returns:** `void`

## FireworkBoostSystem

Processes boost requests each tick. Registered in `GameOrchestrator.startGame()` immediately after `ElytraPhysicsSystem`.

Fully qualified name: `net.elytrarace.server.ecs.system.FireworkBoostSystem`

### Required components

| Component | Role |
|---|---|
| `FireworkBoostComponent` | Boost intent and cooldown state |
| `ElytraFlightComponent` | Flight state and velocity |
| `PlayerRefComponent` | Access to the Minestom `Player` for packet dispatch |

### Per-tick procedure

On each tick the system runs these steps for every matching entity:

1. Call `FireworkBoostComponent.tickCooldown()` to decrement the cooldown counter.
1. Call `FireworkBoostComponent.claimBoostRequest()`. Return immediately if no request is pending.
1. Return immediately if `FireworkBoostComponent.isOnCooldown()` is true or `ElytraFlightComponent.isFlying()` is false.
1. Read the player's yaw and pitch from `Player.getPosition()`.
1. Compute the look-direction unit vector.
1. Scale the unit vector by `BoostConfig.speedBlocksPerTick()` to produce a per-tick velocity.
1. Write the per-tick velocity back to `ElytraFlightComponent.setVelocity()`.
1. Multiply the per-tick velocity by `20` and call `player.setVelocity()` — Minestom expects blocks per second on this API.
1. Call `FireworkBoostComponent.startCooldown()`.
1. Send a `SetCooldownPacket` for `Material.FIREWORK_ROCKET` with the cooldown duration in ticks.

### Look-direction math

The system uses standard Minecraft look-direction math, with yaw=0 pointing south (+Z) and negative pitch looking up:

```java
double yawRad   = Math.toRadians(player.getPosition().yaw());
double pitchRad = Math.toRadians(player.getPosition().pitch());

double lookX = -Math.sin(yawRad) * Math.cos(pitchRad);
double lookY = -Math.sin(pitchRad);
double lookZ =  Math.cos(yawRad) * Math.cos(pitchRad);
```

### Unit conventions

| API | Unit |
|---|---|
| `ElytraFlightComponent.setVelocity(Vec)` | blocks per tick |
| `Player.setVelocity(Vec)` (Minestom) | blocks per second |
| `BoostConfig.speedBlocksPerTick` | blocks per tick |
| `FireworkBoostComponent.cooldownRemainingTicks` | game ticks (20 per second) |

The system converts between the two velocity units by multiplying the per-tick vector by `20.0` before the Minestom call.

## BoostConfig

Per-map firework boost configuration, loaded from the map JSON via `MapDefinition.boostConfig()`.

Fully qualified name: `net.elytrarace.server.cup.BoostConfig`

### Parameters

| Parameter | Type | Description |
|---|---|---|
| `speedBlocksPerTick` | `double` | Total boost speed in blocks per tick. Higher values mean a faster launch. |
| `cooldownMs` | `long` | Minimum milliseconds between two boosts for one player. Converted to ticks by dividing by 50. |

### Default

`BoostConfig.DEFAULT` is `new BoostConfig(2.5, 4_000)` — a 2.5 blocks-per-tick impulse with a 4-second cooldown.

## Signal flow

The event handler never touches velocity or cooldown state. It only signals intent.

```
PlayerUseItemEvent (Netty thread)
  └─ PlayerEventHandler.onUseItem
       └─ FireworkBoostComponent.requestBoost()   // AtomicBoolean → true

Tick N (tick thread)
  └─ FireworkBoostSystem.process
       ├─ tickCooldown()
       ├─ claimBoostRequest()                     // AtomicBoolean → false
       ├─ guard: cooldown or not flying
       ├─ compute impulse from yaw + pitch
       ├─ ElytraFlightComponent.setVelocity(impulse)
       ├─ player.setVelocity(impulse × 20)
       ├─ startCooldown()
       └─ sendPacket(SetCooldownPacket)
```

## Related topics

- [ADR-0001: Move firework boost logic into the ECS](../decisions/0001-firework-boost-in-ecs.md)
- [Register an ECS system](../guides/how-to-register-an-ecs-system.md)
