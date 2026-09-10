# Spike: can a scripted non-player entity glide server-side?

Date: 2026-09-10
Stage: E2a, Task 1
Outcome: **Go.**

## The question

Stage E2a needs recorded Vanilla elytra flights as test fixtures. The obvious way to produce them is a client flying on a real server, which is neither reproducible nor automatable. The alternative this spike tests: drive a **non-player** `LivingEntity` from a Paper plugin, script its rotation per tick, and record the result.

If that works, the whole stage records traces without a Minecraft client and without implementing the network protocol — and it reads the entity's real internal delta movement rather than a velocity reconstructed from position deltas.

## Why it should work

From the decompiled Minecraft 26.2 source, established before the spike:

- `LivingEntity.travel(input)` dispatches to `travelFallFlying(input)` whenever `isFallFlying()`, with no player gating.
- `LivingEntity.aiStep()` calls `travel(input)` when `canSimulateMovement() && isEffectiveAi()`.
- `travelFallFlying` calls `move(MoverType.SELF, …)`, so position and collision resolve server-side.
- `updateFallFlyingMovement` **never reads its `input` parameter**. Only rotation, velocity and gravity affect the result, so mob AI cannot perturb the glide itself.
- Paper patches exactly one of the four elytra methods — `stopFallFlying`, adding a cancellable event. The movement mathematics is Vanilla's, unchanged.

The risk: `aiStep()` gates the `travel()` call on `isEffectiveAi()`. Disabling AI the obvious way might stop the glide with it.

## Method

Paper 26.2 build 123, Java 25, flat world, view and simulation distance 4, no players. A throwaway plugin spawned a zombie at `0, 200, 0` wearing an elytra, called `setGliding(true)`, and each tick read position, velocity, rotation and flags **before** writing the scripted rotation for the next tick. A corridor of chunks was force-loaded — a single chunk is not enough, because an entity crossing into a non-ticking chunk silently stops being ticked.

Four scenarios, 120 ticks each:

| Scenario | AI handling |
|---|---|
| `baseline` | untouched |
| `setAI-false` | `Mob#setAI(false)` |
| `setAware-false` | Paper's `Mob#setAware(false)` |
| `setAware-false-step` | as above, pitch stepped from −5° to −45° at tick 50 |

## Answers

### 1. Does it glide? Yes.

With `setAware(false)` and a scripted pitch of −30°, over 120 ticks:

```
tick=0    y=199.968272  z=0.002339   vy=-0.031728  vz=0.002339
tick=2    y=199.821503  z=0.017514   vy=-0.086281  vz=0.010716
tick=118  y=184.243981  z=35.488706  vy=-0.108525  vz=0.344005
tick=119  y=184.135450  z=35.832733  vy=-0.108531  vz=0.344027
```

Altitude falls slowly while horizontal distance accumulates and horizontal speed rises towards a plateau. That is gliding, not falling: a free fall would show `vy` heading for terminal velocity around −3.9 with no horizontal component at all.

### 2. Does AI interfere with rotation? Yes — decisively, in the baseline.

With AI untouched, the scripted pitch of −30° **never appears**. Every one of the 120 sampled ticks reads back `yaw=0.0000, pitch=0.0000`. The mob's look control overwrites the rotation within the same tick. The entity still glides, but at whatever pitch the AI chooses — which is why the baseline reaches `vz=1.02` against `setAware`'s `0.344`: a flat glide accumulates more speed than a −30° climb.

A recording made without disabling AI would be a recording of the AI's flight, not of the script's.

### 3. Which AI switch works? `setAware(false)`. **`setAI(false)` stops the glide entirely.**

```
setAI-false, every tick 0..119:   y=200.000000  z=0.000000  vy=0.000000  vz=0.000000  gliding=true
```

The entity does not move at all for 120 ticks while `isGliding()` continues to report `true`. This is exactly the failure the spike was designed to catch: `setAI(false)` sets the NoAI flag, `isEffectiveAi()` becomes false, and `aiStep()` therefore never calls `travel()`. The elytra branch is never reached.

`setAware(false)` keeps `hasAI()` true — so `travel()` still runs — while suppressing the AI's decision-making, so the scripted rotation survives:

```
setAware-false, distinct observed pitch: -30.0000
```

**Use `setAware(false)`. Never `setAI(false)`.**

### 4. Is velocity readable and non-trivial? Yes.

`getVelocity()` returns the entity's internal delta movement, changing every tick and settling towards a plateau consistent with drag balancing the lift term. This is better ground truth than a velocity reconstructed from position deltas, and it is what the fixture format records.

## The write/read offset

The step scenario pins it. Rotation written at the end of tick *N* is observed at the start of tick *N+1*, and the flight responds from *N+1* onward:

```
tick=49  obsPitch=-5.0000   setPitch=-5.0    vz=0.446139   (rising)
tick=50  obsPitch=-5.0000   setPitch=-45.0   vz=0.452556   (rising)
tick=51  obsPitch=-45.0000  setPitch=-45.0   vz=0.444090   (falling)
tick=52  obsPitch=-45.0000  setPitch=-45.0   vz=0.435847
```

The steeper climb trades horizontal speed for vertical, exactly as the formula predicts.

**Consequence for the recorder:** write the scripted rotation, let the tick pass, then sample. The reverse order produces a trace offset by one tick, which replays as a constant drift and reads like a formula error.

## What this settles for the rest of the stage

- The recorder needs no Minecraft client and no human pilot.
- `Mob#setAware(false)` is the AI switch; `setAI(false)` is a trap that looks like it works — `isGliding()` still reports true — while the entity never moves.
- Rotation is applied before the tick and the sample taken after it.
- A corridor of chunks must be force-loaded along the flight path, not just the spawn chunk.
- Two premises the plan flagged as unverified are now confirmed: `io.papermc.paper:paper-api:26.2.build.123-stable` resolves from the Paper repository, and plugin-yml's `paper { }` block produces a descriptor a 26.2 server accepts with `api-version: 1.21`.

## What this does not settle

The traces this approach produces validate **the flight formula**. A player's glide is client-authoritative, and the design's plausibility check compares a client's reported positions against a prediction. That end-to-end question belongs to the differential test the design defers to a later stage, and no mob recording can answer it.
