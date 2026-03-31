# Elytra Flight Physics: Complete Reference for Server-Side Implementation

This document serves as a technical reference for implementing elytra flight physics
in Minestom (without vanilla code). All formulas are based on decompiled vanilla code
(Snapshot 15w41b/15w42a, verified up to 1.21.x) and the Minecraft Wiki.

---

## 1. Constants

### 1.1 Elytra Flight Physics

| Constant | Value | Description |
|---|---|---|
| `GRAVITY` | -0.08 | Gravity per tick (blocks/tick^2) |
| `PITCH_LIFT` | 0.06 | Lift coefficient (multiplied by cos^2(pitch)) |
| `DOWNWARD_GLIDE_FACTOR` | -0.1 | Damping factor during downward movement |
| `UPWARD_PITCH_BASE` | 0.04 | Base acceleration when pitching upward |
| `UPWARD_PITCH_MULTIPLIER` | 3.2 | Y-boost multiplier when pitching upward (3.5 in 15w41b, 3.2 from 15w42a) |
| `DIRECTION_ALIGNMENT_RATE` | 0.1 | Rate of alignment to look direction |
| `DRAG_HORIZONTAL` | 0.99 | Horizontal friction coefficient per tick |
| `DRAG_VERTICAL` | 0.98 | Vertical friction coefficient per tick |
| `MIN_FALL_DISTANCE` | 1.0 | Minimum fall height to activate (blocks) |
| `TICKS_PER_SECOND` | 20 | Server tick rate |

### 1.2 General Entity Physics

| Constant | Value | Description |
|---|---|---|
| `ENTITY_GRAVITY` | 0.08 | Standard gravity for entities (blocks/tick^2) |
| `AIR_DRAG` | 0.98 | Vertical drag in the air |
| `GROUND_FRICTION` | 0.546 | Horizontal friction on the ground (0.6 * 0.91) |
| `AIR_FRICTION` | 0.91 | Horizontal friction in the air |
| `PLAYER_HITBOX_GLIDING` | 0.6 x 0.6 x 0.6 | Hitbox during gliding |

### 1.3 Firework Boost

| Constant | Value | Description |
|---|---|---|
| `FIREWORK_HORIZONTAL_ACCEL` | 1.15 | Multiplier for X/Z velocity per tick |
| `FIREWORK_VERTICAL_ACCEL` | 0.04 | Additive vertical acceleration per tick |
| `FIREWORK_INITIAL_VY` | 0.05 | Initial vertical velocity |
| `FIREWORK_INITIAL_VXZ_STDDEV` | 0.001 | Standard deviation of initial X/Z velocity |

### 1.4 Damage Constants

| Constant | Value | Description |
|---|---|---|
| `COLLISION_DAMAGE_MULTIPLIER` | 10 | Multiplier for horizontal velocity change |
| `COLLISION_DAMAGE_OFFSET` | -3 | Subtracted from damage calculation |
| `DURABILITY_DRAIN_INTERVAL` | 20 | Ticks between durability loss (= 1/second) |
| `MAX_DURABILITY` | 432 | Maximum durability (Java Edition) |
| `CRITICAL_IMPACT_ANGLE` | 50 deg | Approximate critical impact angle |

---

## 2. Tick-by-Tick Pseudocode of Elytra Physics

```
Source: Decompiled vanilla code (samsartor gist, verified with Yarn mappings)
Method: LivingEntity.travel() -> Elytra branch when isFallFlying() == true
```

### 2.1 Check Prerequisites

```pseudocode
function canStartGliding(player):
    if player.isCreative:
        return player.glideTime > 0
    else:
        return !player.isOnGround AND player.fallDistance >= 1.0
```

### 2.2 Main Physics Tick (once per tick, 20x/second)

```pseudocode
function elytraPhysicsTick(player, deltaTime = 1):
    // --- Step 1: Durability ---
    if !player.isCreative:
        if (player.glideTime + 1) % 20 == 0:
            player.elytraDurability -= 1

    // --- Step 2: Calculate look direction ---
    yaw   = player.yaw      // Horizontal rotation in radians
    pitch = player.pitch     // Vertical rotation in radians

    yawCos   = cos(-yaw - PI)
    yawSin   = sin(-yaw - PI)
    pitchCos = cos(pitch)
    pitchSin = sin(pitch)

    // Normalized look vector
    lookX = yawSin * (-pitchCos)
    lookY = -pitchSin
    lookZ = yawCos * (-pitchCos)

    // --- Step 3: Helper values ---
    hVel       = sqrt(velX^2 + velZ^2)    // Horizontal velocity
    hLook      = pitchCos                   // Horizontal component of look direction
    sqrPitchCos = pitchCos * pitchCos       // cos^2(pitch)

    // --- Step 4: Gravity + Lift ---
    // Gravity: -0.08
    // Lift:    +0.06 * cos^2(pitch)
    // -> At pitch=0 (horizontal): net = -0.08 + 0.06 = -0.02 (slow descent)
    // -> At pitch=90 (straight down): net = -0.08 + 0 = -0.08 (full gravity)
    velY += GRAVITY + sqrPitchCos * PITCH_LIFT
    // velY += -0.08 + cos^2(pitch) * 0.06

    // --- Step 5: Downward Glide Damping ---
    // When the player is sinking AND looking horizontally (hLook > 0)
    // -> Part of the downward motion is converted to forward motion
    if velY < 0 AND hLook > 0:
        yAcc = velY * DOWNWARD_GLIDE_FACTOR * sqrPitchCos
        // yAcc = velY * (-0.1) * cos^2(pitch)
        velY += yAcc                          // Reduces sink rate
        velX += lookX * yAcc / hLook          // Converts to horizontal motion
        velZ += lookZ * yAcc / hLook

    // --- Step 6: Upward Pitch Boost ---
    // When player looks upward (pitch < 0), horizontal
    // velocity is converted to altitude
    if pitch < 0:
        yAcc = hVel * (-pitchSin) * UPWARD_PITCH_BASE
        // yAcc = hVel * (-sin(pitch)) * 0.04
        velY += yAcc * UPWARD_PITCH_MULTIPLIER   // velY += yAcc * 3.2
        velX -= lookX * yAcc / hLook              // Reduces horizontal velocity
        velZ -= lookZ * yAcc / hLook

    // --- Step 7: Direction Alignment ---
    // Gradually aligns horizontal movement to look direction
    if hLook > 0:
        velX += (lookX / hLook * hVel - velX) * DIRECTION_ALIGNMENT_RATE
        velZ += (lookZ / hLook * hVel - velZ) * DIRECTION_ALIGNMENT_RATE
        // Interpolation with factor 0.1 between current and desired direction

    // --- Step 8: Friction/Drag ---
    velX *= DRAG_HORIZONTAL    // velX *= 0.99
    velY *= DRAG_VERTICAL      // velY *= 0.98
    velZ *= DRAG_HORIZONTAL    // velZ *= 0.99

    // --- Step 9: Update Position ---
    posX += velX
    posY += velY
    posZ += velZ

    // --- Step 10: Collision Detection ---
    performCollisionDetection(player)

    player.glideTime += 1
```

### 2.3 Equilibrium Analysis

At constant pitch (equilibrium state, velY = 0):

```
From Step 4:  0 = -0.08 + cos^2(pitch) * 0.06
              cos^2(pitch) = 0.08 / 0.06 = 1.333...

Since cos^2(pitch) is at most 1.0, there is NO true equilibrium.
At pitch = 0 (horizontal): net Y acceleration = -0.02 (always descending)
```

This means: Without firework boost, the player ALWAYS descends. By periodically
pulling up and diving, speed conversion can maintain altitude.

**Glide ratio**: ~10.06 blocks horizontal per 1 block of altitude loss (at optimal pitch).

**Minimum speed**: ~7.2 m/s (at ~30 degrees upward pitch at the altitude peak).

**Stall condition**: Above 30 degrees upward pitch -> speed drops rapidly.
At 60 degrees -> fall damage possible. At 90 degrees -> free fall.

---

## 3. Firework Boost Calculation

### 3.1 Firework Lifetime

```pseudocode
function calculateFireworkLifetime(gunpowderCount):
    // gunpowderCount = Flight Duration (1-3)
    lifetime = 10 * (gunpowderCount + 1) + random(0, 5) + random(0, 6)
    return lifetime  // in ticks

// Examples:
// Flight Duration 1: 20 + 0..11 = 20-31 ticks (~1.0-1.55 seconds)
// Flight Duration 2: 30 + 0..11 = 30-41 ticks (~1.5-2.05 seconds)
// Flight Duration 3: 40 + 0..11 = 40-51 ticks (~2.0-2.55 seconds)
```

### 3.2 Boost Physics per Tick

```pseudocode
function fireworkBoostTick(player, fireworkEntity):
    if !player.isFallFlying():
        return

    // Player's look direction as normalized vector
    lookDir = player.getLookDirection()  // (lookX, lookY, lookZ) normalized

    // Increase velocity by look direction component
    // The rocket accelerates the player in the look direction
    player.velX += lookX * 0.1 + (lookX * 1.5 - player.velX) * 0.5
    player.velY += lookY * 0.1 + (lookY * 1.5 - player.velY) * 0.5
    player.velZ += lookZ * 0.1 + (lookZ * 1.5 - player.velZ) * 0.5

    // ALTERNATIVE (simplified vanilla approximation):
    // Per tick, velocity is accelerated with the direction vector:
    //   deltaV = lookDir * boostStrength
    //   player.velocity += deltaV
    // Resulting velocity: ~33.5 blocks/second (1.675 blocks/tick)
```

### 3.3 Simplified Boost Implementation for Minestom

```pseudocode
function applyFireworkBoost(player, flightDuration):
    lifetime = calculateFireworkLifetime(flightDuration)
    boostPerTick = 1.675 / lifetime  // Total speed / lifetime

    // Every tick during the lifetime:
    function onTick():
        if ticksRemaining <= 0: return

        lookDir = normalize(player.getLookDirection())
        // Add boost in look direction
        player.velocity += lookDir * boostPerTick

        ticksRemaining -= 1
```

**Maximum speeds with fireworks:**
- Sustained: ~33.5 blocks/second
- Peak bursts: 60-80+ blocks/second (with sustained boosting)

---

## 4. Collision Damage Formula

### 4.1 Impact Damage

```pseudocode
function calculateCollisionDamage(velocityBefore, velocityAfter):
    // Only HORIZONTAL velocity change counts
    deltaHVel = sqrt(velocityBefore.x^2 + velocityBefore.z^2)
              - sqrt(velocityAfter.x^2 + velocityAfter.z^2)

    // Formula from the Minecraft Wiki:
    damage = ceil(COLLISION_DAMAGE_MULTIPLIER * deltaHVel - COLLISION_DAMAGE_OFFSET)
    // damage = ceil(10 * deltaHorizontalVelocity - 3)

    if damage < 0: damage = 0
    return damage
```

### 4.2 Rules

- **Horizontal surfaces** (ceilings): NO damage (only vertical velocity change).
- **Vertical surfaces** (walls): Damage proportional to horizontal velocity change.
- **Diagonal surfaces**: Damage based only on the horizontal component.
- **Critical impact angle**: ~50 degrees to the wall -> maximum damage.
- **Feather Falling**: Has NO effect on elytra collision damage.
- **Slow Falling Potion**: Drastically reduces horizontal and vertical velocity.

### 4.3 Example Calculations

```
At 20 blocks/second horizontal (1.0 blocks/tick):
  Full stop: damage = ceil(10 * 1.0 - 3) = 7 hearts (14 HP)

At 33.5 blocks/second (1.675 blocks/tick):
  Full stop: damage = ceil(10 * 1.675 - 3) = 14 hearts (27.5 -> 28 HP) -> Death

At 10 blocks/second (0.5 blocks/tick):
  Full stop: damage = ceil(10 * 0.5 - 3) = 2 hearts (4 HP)

Minimum damage (damage > 0): deltaHVel > 0.3 blocks/tick (6 blocks/second)
```

---

## 5. Ring Passthrough Detection (Geometric Algorithm)

### 5.1 Ring Definition

A ring (checkpoint) in 3D space is defined by:
- **Center** `C = (cx, cy, cz)`: Center point of the ring
- **Normal** `N = (nx, ny, nz)`: Normal vector of the ring plane (normalized)
- **Radius** `R`: Radius of the ring

### 5.2 Algorithm: Line Segment Through Ring Detection

```pseudocode
function checkRingPassthrough(
    P0,     // Player position at start of tick (vec3)
    P1,     // Player position at end of tick (vec3)
    C,      // Ring center (vec3)
    N,      // Ring normal vector (vec3, normalized)
    R       // Ring radius (float)
) -> boolean:

    // --- Step 1: Line Segment-Plane Intersection ---
    // The ring plane is defined as: dot(N, P - C) = 0
    // The line segment is: P(t) = P0 + t * (P1 - P0), t in [0, 1]

    D = P1 - P0                         // Direction vector
    denom = dot(N, D)                    // Denominator

    // If denom ~= 0: Segment is parallel to plane -> no passthrough
    if abs(denom) < EPSILON:
        return false

    // Parameter t for the intersection point
    t = dot(N, C - P0) / denom

    // Check if intersection point lies within the segment
    if t < 0.0 OR t > 1.0:
        return false

    // --- Step 2: Calculate Intersection Point ---
    intersection = P0 + t * D

    // --- Step 3: Check Distance to Ring Center ---
    distToCenter = length(intersection - C)

    // Player flies through the ring if distance is less than radius
    if distToCenter <= R:
        return true

    return false
```

### 5.3 Extended Version with Player Hitbox

```pseudocode
function checkRingPassthroughWithHitbox(
    P0, P1,         // Start/end position (player center)
    C, N, R,         // Ring parameters
    playerRadius     // Half player hitbox width (0.3 during gliding)
) -> boolean:
    // Effective ring radius = ring radius minus player radius
    // (Player must fit completely through the ring)
    effectiveR = R - playerRadius

    if effectiveR <= 0:
        return false  // Ring too small

    return checkRingPassthrough(P0, P1, C, N, effectiveR)
```

### 5.4 Robust Version with Multi-Sampling

At very high speeds, the player can fly completely through the ring in one tick.
For additional robustness:

```pseudocode
function checkRingPassthroughRobust(
    P0, P1, C, N, R, playerRadius, subSteps = 4
) -> boolean:
    // Primary check: Line segment
    if checkRingPassthroughWithHitbox(P0, P1, C, N, R, playerRadius):
        return true

    // Secondary check: Closest point distance
    // (for the case where the ring is very close to the path but not intersected)
    closestPoint = closestPointOnSegment(P0, P1, C)
    if length(closestPoint - C) <= R * 1.5:  // Generous check
        // Project onto ring plane and check again
        projected = closestPoint - dot(closestPoint - C, N) * N
        if length(projected - C) <= R:
            return true

    return false
```

### 5.5 Helper Functions

```pseudocode
function closestPointOnSegment(A, B, P) -> vec3:
    AB = B - A
    t = dot(P - A, AB) / dot(AB, AB)
    t = clamp(t, 0.0, 1.0)
    return A + t * AB

function dot(a, b) -> float:
    return a.x * b.x + a.y * b.y + a.z * b.z

function length(v) -> float:
    return sqrt(v.x^2 + v.y^2 + v.z^2)

function normalize(v) -> vec3:
    l = length(v)
    return vec3(v.x/l, v.y/l, v.z/l)
```

---

## 6. Server-Client Synchronization (Packets)

### 6.1 Relevant Packets

#### Serverbound (Client -> Server)

| Packet | ID (1.21.x) | Description |
|---|---|---|
| **Player Action** (Entity Action) | 0x25 | Contains Action ID `8` = "Start Elytra Flying" |
| **Player Position** | 0x1A | X, Y, Z (Double), On Ground (Boolean) |
| **Player Position and Rotation** | 0x1B | X, Y, Z, Yaw, Pitch, On Ground |
| **Player Rotation** | 0x1C | Yaw, Pitch, On Ground |

#### Clientbound (Server -> Client)

| Packet | ID (1.21.x) | Description |
|---|---|---|
| **Entity Velocity** (Set Entity Motion) | 0x58 | Entity ID (VarInt), Velocity X/Y/Z (Short, in 1/8000 blocks/tick) |
| **Entity Metadata** (Set Entity Data) | 0x58 | Entity ID, Metadata Entries |
| **Player Position** (Synchronize Player Position) | 0x40 | X, Y, Z, Yaw, Pitch, Flags, Teleport ID |
| **Entity Position** (Update Entity Position) | 0x2E | Entity ID, Delta X/Y/Z (Short), On Ground |

### 6.2 Entity Metadata: Elytra Flag

```
Entity Base Class -> Index 0 (Byte):
  Bit 0x01: Is on Fire
  Bit 0x02: Is Crouching
  Bit 0x04: Is Sprinting (unused for players)
  Bit 0x08: Is Swimming
  Bit 0x10: Is Invisible
  Bit 0x20: Has Glowing Effect
  Bit 0x80: Is Flying with Elytra (Fall Flying)
```

**Important**: Bit `0x80` at index 0 controls the elytra flight animation on the client.

### 6.3 Velocity Packet Encoding

```pseudocode
// Velocity is transmitted as Short (16-bit signed) in 1/8000 blocks/tick
function encodeVelocity(blocksPerTick) -> short:
    return clamp(round(blocksPerTick * 8000), -32768, 32767)

function decodeVelocity(rawShort) -> double:
    return rawShort / 8000.0

// Example: 1.675 blocks/tick -> 13400 (Short value)
// Maximum representable speed: 32767/8000 = 4.096 blocks/tick = 81.9 blocks/second
```

### 6.4 Synchronization Strategy for Minestom

```pseudocode
// Recommended server-side implementation:

function onPlayerStartElytraFlying(player):
    // 1. Validation: Does the player have an elytra equipped?
    if !hasElytraEquipped(player): return
    // 2. Validation: Is the player in the air with enough fall height?
    if player.isOnGround OR player.fallDistance < 1.0: return
    // 3. Set elytra flag
    player.setFallFlying(true)
    // 4. Send metadata packet to all players
    broadcastEntityMetadata(player, index=0, bit=0x80, value=true)

function onServerTick():
    for each player in flyingPlayers:
        // 1. Receive client position and rotation (serverbound)
        //    -> Player Position/Rotation packets
        // 2. Calculate server-side physics
        elytraPhysicsTick(player)
        // 3. Send calculated velocity to client (every 1-5 ticks)
        if player.ticksSinceLastVelocityUpdate >= VELOCITY_SYNC_INTERVAL:
            sendEntityVelocity(player, player.velocity)
            player.ticksSinceLastVelocityUpdate = 0
        // 4. Validate position (anti-cheat)
        validatePlayerPosition(player)

// VELOCITY_SYNC_INTERVAL:
// - 1 tick:  Precise, but high bandwidth
// - 3 ticks: Good compromise
// - 5 ticks: Economical, but noticeable lag
```

### 6.5 Anti-Cheat Considerations

```
- The vanilla server allows a maximum of 80 ticks of floating before kick
- Elytra flight must be marked as allowed "floating"
- Check speed upper limit (>81.9 blocks/s is suspicious)
- Validate firework boost time window
```

---

## 7. Summary of the Physics Pipeline

```
Every Tick (50ms):

1. INPUT:     Client sends position + rotation
2. VALIDATE:  Check if elytra flight is active and valid
3. PHYSICS:   Calculate elytra physics:
              a) Gravity + lift
              b) Downward glide -> convert to forward motion
              c) Upward pitch -> altitude from speed
              d) Direction alignment
              e) Drag/friction
4. BOOST:     If firework active: add boost
5. COLLISION: Check collisions with world
              -> Calculate damage on impact
6. RING:      Ring passthrough detection
              -> Check line segment P0->P1 against ring
7. POSITION:  New position = old position + velocity
8. SYNC:      Send velocity packet to client (periodically)
              Update metadata if needed
9. DAMAGE:    Durability update every 20 ticks
```

---

## 8. Sources

- Decompiled Elytra Code (Snapshot 15w41b): https://gist.github.com/samsartor/a7ec457aca23a7f3f120
- Minecraft Wiki - Elytra: https://minecraft.wiki/w/Elytra
- Minecraft Wiki - Firework Rocket: https://minecraft.wiki/w/Firework_Rocket
- Entity Physics: https://github.com/ddevault/TrueCraft/wiki/Entity-Movement-And-Physics
- Minecraft Protocol - Entity Metadata: https://minecraft.wiki/w/Java_Edition_protocol/Entity_metadata
- Minecraft Protocol - Packets: https://minecraft.wiki/w/Java_Edition_protocol/Packets
- wiki.vg Protocol Reference: https://wiki.vg/Protocol
- Minestom Elytra Discussion: https://github.com/Minestom/Minestom/discussions/1427
- 3D Collision Detection (MDN): https://developer.mozilla.org/en-US/docs/Games/Techniques/3D_collision_detection
- Circle-Line Intersection: https://www.baeldung.com/cs/circle-line-segment-collision-detection
