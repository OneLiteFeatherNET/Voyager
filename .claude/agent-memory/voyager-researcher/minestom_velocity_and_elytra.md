---
name: Minestom Velocity and Elytra API
description: Verified behavior of Minestom's velocity API, elytra flight flags, and physics tick for elytra race physics
type: reference
---

## Velocity units (VERIFIED T1 against source 2026-04)
Entity.java field: `protected Vec velocity = Vec.ZERO; // Movement in block per second`

**Velocity is stored in blocks/SECOND**, not blocks/tick.

- `setVelocity(Vec)` expects blocks/second.
- `getVelocity()` returns blocks/second.
- `getVelocityPacket()` / `getVelocityForPacket()` internally divides by `ServerFlag.SERVER_TICKS_PER_SECOND` before sending `EntityVelocityPacket` (packet wire format is blocks/tick, per Minecraft protocol).
- `movementTick()` divides velocity by `SERVER_TICKS_PER_SECOND` before calling `PhysicsUtils.simulateMovement(...)` (which works in blocks/tick).

**Consequence for Voyager:** if Vector (voyager-math-physics) computes elytra per-tick deltas in blocks/tick (matching vanilla's samsartor formula), those values MUST be multiplied by 20 before being passed to `player.setVelocity(...)`, otherwise the player moves at 5% vanilla speed and drifts.

## setVelocity always sends a packet
```java
public void setVelocity(Vec velocity) {
    EntityVelocityEvent ev = new EntityVelocityEvent(this, velocity);
    EventDispatcher.callCancellable(ev, () -> {
        this.velocity = ev.getVelocity();
        sendPacketToViewersAndSelf(getVelocityPacket());  // ALWAYS sent
    });
}
```
There is no public "set internal velocity only" API. To drive the client without resending every tick you'd need a fork or subclass that exposes the `velocity` field directly.

Periodic auto-sync also happens in `tick()` when not in a vehicle and sync interval elapsed.

## movementTick has a Player bail-out
`Entity.movementTick()` runs physics simulation for ALL entities including Players, but then:
```
if (!(this instanceof Player)) {
    onGround = physicsResult.isOnGround();
    refreshPosition(...);
}
```
i.e., for Players the computed physics RESULT is discarded and position is NOT refreshed from server physics — the client's MOVE_PLAYER_POS_ROT is trusted. This is the architectural reason Minestom elytra "works" visually with zero server physics: the client runs vanilla elytra sim locally and reports positions.

## Elytra state API
- `LivingEntity.isFlyingWithElytra()` → reads entityMeta flag (byte mask on LivingEntityMeta)
- `LivingEntity.setFlyingWithElytra(boolean)` → sets meta flag AND calls `updatePose()` (sets pose to FALL_FLYING)
- Server does NOT auto-start elytra flight when chestplate + sneaking-mid-air occurs. The vanilla client sends `ClientPlayerCommandPacket` with `START_FALL_FLYING`; Minestom's `ClientCommandPacketListener` handles this and flips the flag.
- `Player.refreshOnGround(true)` calls `setFlyingWithElytra(false)` and dispatches `PlayerStopFlyingWithElytraEvent`.

There is NO way to "disable Minestom's built-in elytra physics" because **there is no built-in elytra physics to disable.** The bail-out above means Minestom already does nothing; the client is authoritative.

## No server-side firework simulation
- `EntityType.FIREWORK_ROCKET` exists as a spawnable entity type (wire-level).
- There is NO `FireworkRocketEntity` subclass that boosts its rider each tick. Spawning a firework entity server-side shows the visual but does NOT move the player.
- `PlayerUseItemEvent` fires when a player right-clicks with an item (including a firework rocket). It is the correct hook for detecting the boost input. Vanilla client will also predict the boost locally if elytra-flying → server must push matching velocity each tick to stay in sync.

## PlayerMoveEvent
- Cancellable.
- `setNewPosition(Pos)` can rewrite the destination.
- Fires on every client position update. This is the intercept point if you want to VALIDATE or REJECT client motion — e.g., teleport back if speed exceeds cap, or force a position during a server-authoritative segment.

## Actionable recipe for server-authoritative elytra boost
1. Listen for `PlayerUseItemEvent` with a firework rocket while `player.isFlyingWithElytra()`.
2. Spawn the visual firework entity for other viewers (purely cosmetic).
3. Each tick for the firework's duration (vanilla: ~1.17/1.48/2.22 s for flight duration 1/2/3):
   - Read current `player.getPosition().direction()` (look vector)
   - Compute new velocity per vanilla formula (per-tick math) → multiply by 20 → `player.setVelocity(vec)`
4. Stop early on `PlayerStopFlyingWithElytraEvent`.

Sending `setVelocity` every tick IS the intended pattern. It causes no stutter as long as the velocity sequence is smooth (matches what the client was about to predict anyway). Stutter occurs only when server velocity sharply disagrees with client prediction — i.e., when the math is wrong.

## Source tiers
- Velocity units comment in Entity.java: T1-verified (read source 2026-04)
- setVelocity packet send: T1-verified (source)
- movementTick Player bail-out: T1-verified (source)
- setFlyingWithElytra pose update: T1-verified (LivingEntity.java)
- No FireworkRocketEntity class: T2-official (absent from allclasses index 2026.04.13)
- PlayerMoveEvent cancellable + setNewPosition: T2-official (javadoc)
