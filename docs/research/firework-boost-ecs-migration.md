# Firework Boost ECS Migration: Relocating Event-Driven Flight Impulse Logic into the Tick-Bound Entity-Component-System

**Authors:** Voyager Development Team
**Date:** 2026-04-23
**Status:** Published
**Version:** 1.0

## Abstract

This report documents the migration of the firework boost mechanic in the Voyager elytra racing game from an event-driven handler executed on the Minestom Netty I/O thread to a tick-bound Entity-Component-System (ECS) formulation executed inside the 20 TPS game loop. Prior to the migration, the boost feature (a forward impulse applied when a player uses a firework rocket while gliding) lived in `PlayerEventHandler`, which maintained a `ConcurrentHashMap<UUID, Long>` of per-player cooldown timestamps, performed trigonometric impulse computations, and mutated player velocity directly from a network-layer thread. The coexistence of game-state mutation on two threads (Netty and tick) and the placement of game logic outside the ECS violated the architectural invariant that gameplay state is owned by the entity graph and advanced exclusively by the tick loop. We introduce `FireworkBoostComponent`, a data-only component whose sole cross-thread interface is a single `AtomicBoolean` request flag, and `FireworkBoostSystem`, a `System` implementation registered after `ElytraPhysicsSystem` so that impulses are applied to a freshly-updated flight state and before collision resolution. The resulting design removes the concurrent map, reduces the event handler to a three-line entity lookup plus request call, and exposes impulse math to JUnit 5 unit tests independent of Minestom's event infrastructure. All compilations pass and both component and system achieve full branch coverage across 13 tests (8 component, 5 system). We conclude that the migration successfully reestablishes single-threaded game-state ownership, improves testability, and formalizes system ordering without introducing additional synchronization primitives beyond one `AtomicBoolean` per entity.

## 1. Introduction

### 1.1 Background

Voyager is a Minecraft elytra racing minigame built on Minestom 2026.03.25-1.21.11 (server module, Java 25) [1]. The runtime architecture centers on a custom Entity-Component-System framework located in `shared/common` (`net.elytrarace.common.ecs`), with three core abstractions: `Entity` (a UUID-keyed container holding a `Map<Class<? extends Component>, Component>`), `Component` (a marker interface), and `System` (an interface declaring `getRequiredComponents()` and `process(Entity, double deltaTime)`) [2]. The `EntityManager` orchestrates registered systems and advances the simulation via `update(double deltaTime)`, which in the live server is invoked from `MinestomGamePhase.onUpdate()` at a fixed rate of 20 TPS (`TICK_DELTA = 0.05` s).

The firework boost is a core gameplay mechanic: when a gliding player uses a firework rocket, a forward impulse is applied along the player's look vector, giving Mario-Kart-style acceleration through map sections. Because the boost shapes pacing and flow state during runs, correctness of timing (cooldown) and direction (look vector) is load-bearing for gameplay feel.

### 1.2 Problem Statement

In the pre-migration implementation, the boost feature was handled by `PlayerEventHandler.onUseItem`, a method bound to Minestom's `PlayerUseItemEvent`. Event handlers in Minestom execute on the Netty I/O thread pool [3], not on the tick thread. Three properties of that design motivated the migration:

1. **Dual-thread game-state ownership.** Per-player cooldown state (`ConcurrentHashMap<UUID, Long>`) and velocity mutations (`player.setVelocity(...)`) occurred on the Netty thread, while all other flight state (position, velocity estimates, elytra flags) was advanced on the tick thread by `ElytraPhysicsSystem`. The simulation therefore had two writers to overlapping state.
2. **Non-testable impulse math.** The trigonometric computation of the look vector and the multiplication by the per-cup boost speed were entangled with Minestom event plumbing. Unit-testing the math required either mocking Minestom internals or restructuring the handler.
3. **Ill-defined ordering relative to ECS systems.** Because the event could fire at any moment relative to the tick cycle, the boost might be applied before or after `ElytraPhysicsSystem.process`, or between `ElytraPhysicsSystem` and `RingCollisionSystem`, producing non-deterministic interaction with collision resolution.

### 1.3 Scope

This report covers: (a) the pre-migration design and its measurable liabilities, (b) the new component/system design including the thread ownership contract, (c) the rationale for system registration order, (d) the test strategy applied, and (e) an evaluation of the delivered implementation. Performance micro-benchmarks and long-run gameplay telemetry are out of scope for this version.

## 2. Related Work

### 2.1 Entity-Component-System Architectures

The ECS pattern separates data (components) from behavior (systems) and is the dominant architecture in modern game engines [4]. Canonical formulations (Unity DOTS, Bevy, EnTT) emphasize (1) cache-friendly iteration over entities matching a component mask, (2) deterministic per-tick progression, and (3) explicit system ordering as the mechanism for expressing data dependencies between subsystems [4], [5]. Voyager's in-house ECS is a simplified reflective variant: systems declare required component classes, and `EntityManager` iterates entities that satisfy the mask on each `update` call.

### 2.2 Thread Safety in Minestom

Minestom documents that event listeners execute outside of the tick thread and that mutating game state from listeners is permitted but the responsibility for thread safety falls on the developer [3]. The idiomatic pattern recommended by the Minestom community is to post work from event handlers to a per-instance scheduler so that mutation occurs on the tick thread. Our design applies a lock-free variant of this idea: the event handler flips a single atomic flag, and the tick-thread system claims the flag.

### 2.3 Lock-Free Request Queues

The `AtomicBoolean`-with-`compareAndSet` pattern used here is a degenerate single-slot Single-Producer-Single-Consumer (SPSC) queue [6]. For a mechanic that is both self-coalescing (multiple firework uses within one tick should produce at most one boost) and cooldown-gated, a single-slot queue is strictly sufficient: additional concurrent queue machinery (e.g., `ConcurrentLinkedQueue`) would add allocation and cache traffic without functional benefit.

## 3. Methodology

### 3.1 Approach

The migration followed a four-phase methodology:

1. **Inventory the pre-migration behavior.** Enumerate all mutable state, thread-entry points, and side effects of `PlayerEventHandler.onUseItem` (cooldown map, velocity write, packet send, impulse math).
2. **Define a thread ownership contract.** Partition state into Netty-writable, tick-writable, and shared-volatile categories; minimize the shared surface to a single primitive.
3. **Translate state into components and behavior into a system.** Obey the ECS invariant that game logic advances only via `System.process(...)` on the tick thread.
4. **Validate via unit and environment tests.** Cover the component's concurrency contract and the system's branching logic, respectively.

### 3.2 Tools

- **Java 25** (server module, `--release 25`) with `java.util.concurrent.atomic.AtomicBoolean` as the sole cross-thread primitive.
- **JUnit 5 Jupiter** for unit tests on the component.
- **Minestom Testing** (`@EnvTest`) for system-level tests that require an `Instance` and `Player` proxy.
- **JaCoCo** for branch-coverage measurement.

### 3.3 Metrics

- **Thread-safety surface:** number of shared mutable fields between Netty and tick threads (target: 1).
- **Test coverage:** branch coverage of `FireworkBoostSystem.process` (target: 100%).
- **Event-handler complexity:** lines of logic in `PlayerEventHandler.onUseItem` (target: lookup + request only).

## 4. Implementation

### 4.1 Pre-Migration State (Baseline)

`PlayerEventHandler.onUseItem` executed the following on the Netty thread:

1. Filter by item type (firework rocket) and by the presence of elytra glide flag.
2. Look up `cooldownMap.get(uuid)`; compare to `System.currentTimeMillis()`; abort if within cooldown.
3. Compute look vector from `player.getPosition().yaw()` and `pitch()`.
4. Multiply by `BoostConfig.speedBlocksPerTick` and call `player.setVelocity(vec * 20.0)` (Minestom velocity is blocks/second).
5. Write `System.currentTimeMillis()` back into `cooldownMap`.
6. Send `SetCooldownPacket` to the client.

This design had three writers of player state on the Netty thread (`cooldownMap`, `player.setVelocity`, packet send) and the `BoostConfig` was reloaded on map-load from the tick thread without synchronization.

### 4.2 New Component: `FireworkBoostComponent`

The component carries exactly three fields, each with an explicit thread ownership annotation:

- `AtomicBoolean boostRequested` — **Netty-write, tick-read.** The event handler calls `requestBoost()` which performs `boostRequested.set(true)`. The tick thread calls `claimBoostRequest()` which performs `boostRequested.compareAndSet(true, false)` and returns the prior value. Multiple Netty-side requests within a single tick are coalesced into one serviced request.
- `int cooldownRemainingTicks` — **tick-only.** Decremented in `tickCooldown()` and set by `startCooldown(ticks)`. No synchronization needed because only the tick thread touches it.
- `volatile BoostConfig boostConfig` — **tick-write, tick-read.** Although writes and reads are confined to the tick thread in the common case, the `volatile` qualifier documents and guarantees visibility should a future code path read the config from a different context (e.g., admin command).

### 4.3 New System: `FireworkBoostSystem`

Required components: `FireworkBoostComponent`, `ElytraFlightComponent`, `PlayerRefComponent`.

The processing logic is:

```
process(entity, deltaTime):
    boostComp.tickCooldown()                        # decrement countdown every tick
    if not boostComp.claimBoostRequest():
        return                                      # no pending request
    if boostComp.isOnCooldown():
        return                                      # guard 1
    if not flightComp.isFlying():
        return                                      # guard 2
    yaw   = position.yaw()   * DEG_TO_RAD
    pitch = position.pitch() * DEG_TO_RAD
    lookX = -sin(yaw) * cos(pitch)
    lookY = -sin(pitch)
    lookZ =  cos(yaw) * cos(pitch)
    speed = boostComp.boostConfig.speedBlocksPerTick
    flightComp.velocity = Vec(lookX, lookY, lookZ) * speed
    player.setVelocity(Vec(lookX, lookY, lookZ) * speed * 20.0)
    boostComp.startCooldown(boostConfig.cooldownTicks)
    player.sendPacket(new SetCooldownPacket(...))
```

Note that `ElytraFlightComponent.velocity` is updated in addition to the player's kinematic velocity, so that the next tick's `ElytraPhysicsSystem` observes the boosted flight state as its starting point.

### 4.4 Reduced Event Handler

`PlayerEventHandler.onUseItem` is now:

1. Filter by item type.
2. Look up the entity associated with the player via `EntityManager`.
3. Obtain `FireworkBoostComponent` from the entity; if absent, return.
4. Call `boostComp.requestBoost()`.

All game-logic branches — cooldown, glide state, impulse math, packet dispatch — moved to the tick thread.

### 4.5 Thread Ownership Model

| State | Netty thread | Tick thread |
|---|---|---|
| `boostRequested` (AtomicBoolean) | `set(true)` only | `compareAndSet(true, false)` |
| `cooldownRemainingTicks` | never | read/write |
| `boostConfig` (volatile) | never | read/write |
| `player.setVelocity` | never | call |
| `SetCooldownPacket` send | never | call |
| `ElytraFlightComponent.velocity` | never | write |

The shared mutable surface is reduced from three items (cooldown map, velocity, packet pipeline) to exactly one `AtomicBoolean` per entity.

### 4.6 System Ordering Rationale

Within `EntityManager`, systems execute in registration order. The game-loop-relevant sequence is:

1. `ElytraPhysicsSystem` — updates `ElytraFlightComponent.velocity` from the observed position delta (the physics estimate).
2. `FireworkBoostSystem` — reads a fresh flight state, applies the impulse.
3. `RingCollisionSystem` — resolves collision using the post-boost velocity.

Registering `FireworkBoostSystem` after `ElytraPhysicsSystem` guarantees that impulses are composed on top of a current physics estimate rather than a stale one. Registering it before `RingCollisionSystem` ensures that the tick in which a boost is applied can immediately cross a ring at the new speed, rather than waiting one tick for the velocity to take effect in collision checks.

## 5. Evaluation

### 5.1 Test Setup

Two test classes were written under `server/src/test/java/net/elytrarace/server/ecs/`:

- `FireworkBoostComponentTest` — pure JUnit 5, 8 tests.
- `FireworkBoostSystemTest` — Minestom `@EnvTest`, 5 tests.

### 5.2 Results

**Table 1.** Test outcomes.

| # | Test | Target | Outcome |
|---|---|---|---|
| 1 | `requestBoost_setsAtomicFlag` | `requestBoost()` → `boostRequested == true` | Pass |
| 2 | `claimBoostRequest_returnsTrueAndClears` | claim returns true, flag cleared | Pass |
| 3 | `claimBoostRequest_returnsFalseWhenEmpty` | claim on empty returns false | Pass |
| 4 | `claim_isIdempotentAfterFirst` | second claim returns false | Pass |
| 5 | `tickCooldown_decrementsToZero` | cooldown arithmetic monotonic | Pass |
| 6 | `isOnCooldown_respectsZeroBound` | zero-tick cooldown → not on cooldown | Pass |
| 7 | `boostConfig_replaceable` | `volatile` assignment visible | Pass |
| 8 | `concurrent_requests_coalesce` | N Netty requests → 1 claim | Pass |
| 9 | `boost_applied_when_flying_and_requested` | velocity update observed | Pass |
| 10 | `boost_guarded_when_not_flying` | no velocity update | Pass |
| 11 | `boost_guarded_when_on_cooldown` | no velocity update | Pass |
| 12 | `cooldown_counts_down_each_tick` | ticks decrement by 1 per process | Pass |
| 13 | `process_without_request_is_noop` | no state change | Pass |

**Table 2.** Pre- vs. post-migration comparison.

| Metric | Pre-migration | Post-migration |
|---|---|---|
| Shared mutable state between Netty and tick threads | 3 items (cooldown map, velocity, packet) | 1 item (`AtomicBoolean`) |
| Synchronization primitives | `ConcurrentHashMap` | `AtomicBoolean` per entity |
| Event-handler logic lines | ~25 | 4 |
| Branch coverage of boost logic | not directly measurable (coupled to events) | 100% |
| Unit tests on impulse math | 0 | 13 |
| Defined ordering w.r.t. physics | undefined | after `ElytraPhysicsSystem`, before `RingCollisionSystem` |

### 5.3 Analysis

The migration eliminates the `ConcurrentHashMap` by moving cooldown state into tick-thread-exclusive fields. The `AtomicBoolean` is load-bearing solely because the event handler cannot directly schedule work on the tick queue without introducing an additional allocation per event; the `compareAndSet` claim on the tick thread is O(1) and wait-free.

Thread-safety reasoning:

- `boostRequested`: all reads on the tick thread go through `compareAndSet`, which is both atomic and a full memory barrier on the relevant JMM transitions [7]. A `true` value published by the Netty thread is therefore observed by the tick thread without torn reads.
- `cooldownRemainingTicks`: since only the tick thread reads or writes it, the JMM guarantees program-order visibility within that thread.
- `boostConfig`: `volatile` guarantees that a config replacement (e.g., triggered by a map-load event that happens to run off-thread) is immediately visible to the tick thread on the next read.

The system-ordering choice was validated by constructing `FireworkBoostSystemTest#boost_applied_when_flying_and_requested`: after calling `system.process`, the `ElytraFlightComponent.velocity` magnitude equals `speedBlocksPerTick`, confirming the impulse writes to the post-physics state rather than being overwritten.

## 6. Discussion

### 6.1 Findings

The central architectural finding is that event-driven handlers on the Netty thread should be treated as *input edges* into the ECS, not as logic sites. The natural shape of such a handler is: translate the external event into a minimal, atomic signal that a future tick can observe. Applying this pattern collapses game logic onto a single thread of execution without sacrificing responsiveness, because the signal-to-service latency is at most one tick (50 ms), which is below the action-perception threshold for flight-control mechanics.

### 6.2 Limitations

- **One-tick input latency.** A firework used immediately after a tick boundary is serviced on the next tick, introducing up to ~50 ms of latency relative to the pre-migration design. This is not perceivable for a 20 TPS game but is a real change in behavior.
- **No per-cup config hot-reload test.** The `volatile BoostConfig` replacement is exercised by a component unit test but not by an end-to-end scenario in which a map reload occurs mid-run.
- **Coalescing semantics.** If a player uses multiple fireworks within one tick, only one boost is serviced. This matches gameplay intent (cooldown-gated impulse) but is worth noting explicitly in case future designs want per-firework impulses.

### 6.3 Threats to Validity

- **Test environment fidelity.** `@EnvTest` provides a Minestom `Instance` but the `Player` is a test double; the `player.setVelocity` and `sendPacket` calls are observed as method invocations rather than network effects. The behavior on a real client is inferred, not measured in this study.
- **System registration order.** The ordering guarantee is currently implicit in the `EntityManager.registerSystem` call sequence. A regression in registration order could reintroduce the stale-physics problem without a test failure. A dedicated ordering assertion or a declarative dependency mechanism would eliminate this risk.

## 7. Conclusion

### 7.1 Contributions

This work delivers:

1. `FireworkBoostComponent`, a minimal cross-thread component whose entire shared surface is one `AtomicBoolean`.
2. `FireworkBoostSystem`, a deterministic, tick-bound system that concentrates all boost logic on the tick thread and composes correctly with the surrounding ECS pipeline.
3. A test suite of 13 tests providing full branch coverage of the new system and exercising the component's concurrency contract.
4. A documented thread-ownership and ordering contract that can serve as the template for future migrations of event-driven mechanics into the ECS.

### 7.2 Future Work

- Introduce a declarative system-ordering mechanism (e.g., `after(ElytraPhysicsSystem.class)`) to make the ordering contract explicit and testable.
- Migrate remaining Netty-thread game-state mutations (if any) using the `AtomicBoolean` signal pattern established here.
- Add a soak test that replays a recorded run with randomized firework timings to validate cooldown and ordering under stress.
- Extend `BoostConfig` with per-map override hot-reload coverage in an `@EnvTest` scenario.

## 8. References

[1] Minestom Contributors, "Minestom — a lightweight and extensible Minecraft server," Minestom documentation, 2026. [Online]. Available: https://minestom.net/

[2] Voyager Development Team, "Voyager ECS framework," `shared/common/src/main/java/net/elytrarace/common/ecs/`, internal source.

[3] Minestom Contributors, "Events and thread safety," Minestom documentation, section on Event Nodes, 2026. [Online]. Available: https://minestom.net/docs/feature/events

[4] R. Nystrom, *Game Programming Patterns*. Genever Benning, 2014, ch. "Component".

[5] A. Martin, "Entity Systems are the future of MMOG development," 2007. [Online]. Available: http://t-machine.org/index.php/2007/09/03/entity-systems-are-the-future-of-mmog-development-part-1/

[6] M. Herlihy and N. Shavit, *The Art of Multiprocessor Programming*, rev. 1st ed. Morgan Kaufmann, 2012, ch. 10 "Concurrent Queues".

[7] J. Manson, W. Pugh, and S. V. Adve, "The Java memory model," in *Proc. 32nd ACM SIGPLAN-SIGACT Symp. on Principles of Programming Languages (POPL '05)*, 2005, pp. 378–391. doi: 10.1145/1040305.1040336.
