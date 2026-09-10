# Elytra flight in Minecraft 26.2 — verified against decompiled source

Derived from the decompiled Mojang-mapped server for **Minecraft 26.2** (`mcVersion=26.2`,
`channel=STABLE`), obtained through PaperMC's `paperweight` `setupMacheSources` task. This supersedes
the wiki-derived parts of `docs/elytra-physics-reference.md`, which were established by absence of
changelog entries rather than by reading the code.

Source of record: `net/minecraft/world/entity/LivingEntity.java`, methods `travelFallFlying`,
`updateFallFlyingMovement`, `handleFallFlyingCollisions`, `getEffectiveGravity`.

## The tick, as it actually reads

```java
private void travelFallFlying(final Vec3 input) {
    if (this.onClimbable()) {
        this.travelInAir(input);
        this.stopFallFlying();
    } else {
        Vec3 lastMovement = this.getDeltaMovement();
        double lastSpeed = lastMovement.horizontalDistance();
        this.setDeltaMovement(this.updateFallFlyingMovement(lastMovement));
        this.move(MoverType.SELF, this.getDeltaMovement());
        if (!this.level().isClientSide()) {
            double newSpeed = this.getDeltaMovement().horizontalDistance();
            this.handleFallFlyingCollisions(lastSpeed, newSpeed);
        }
    }
}

private Vec3 updateFallFlyingMovement(Vec3 movement) {
    Vec3 lookAngle = this.getLookAngle();
    float leanAngle = this.getXRot() * Mth.DEG_TO_RAD;
    double lookHorLength = Math.sqrt(lookAngle.x * lookAngle.x + lookAngle.z * lookAngle.z);
    double moveHorLength = movement.horizontalDistance();
    double gravity = this.getEffectiveGravity();
    double liftForce = Mth.square(Math.cos(leanAngle));
    movement = movement.add(0.0, gravity * (-1.0 + liftForce * 0.75), 0.0);
    if (movement.y < 0.0 && lookHorLength > 0.0) {
        double convert = movement.y * -0.1 * liftForce;
        movement = movement.add(lookAngle.x * convert / lookHorLength, convert, lookAngle.z * convert / lookHorLength);
    }

    if (leanAngle < 0.0F && lookHorLength > 0.0) {
        double convert = moveHorLength * -Mth.sin(leanAngle) * 0.04;
        movement = movement.add(-lookAngle.x * convert / lookHorLength, convert * 3.2, -lookAngle.z * convert / lookHorLength);
    }

    if (lookHorLength > 0.0) {
        movement = movement.add(
            (lookAngle.x / lookHorLength * moveHorLength - movement.x) * 0.1, 0.0, (lookAngle.z / lookHorLength * moveHorLength - movement.z) * 0.1
        );
    }

    return movement.multiply(0.99F, 0.98F, 0.99F);
}
```

## Corrections to the existing reference

| # | Existing claim | What the source says |
|---|---|---|
| 1 | Gravity is the constant `-0.08` and lift is `+0.06·cos²` | Neither is a constant. The term is `gravity * (-1.0 + liftForce * 0.75)` with `gravity = getEffectiveGravity()`. At the default gravity attribute of `0.08` this evaluates to `-0.08 + cos²·0.06`, which is why the constants looked right — but a gravity modifier scales **both** halves. |
| 2 | Vanilla divides unguarded at the pitch-boost step, so our extra `hLook > 1e-8` guard is a deliberate divergence | Vanilla guards all three branches with `lookHorLength > 0.0`. The divergence is only the threshold, not the presence of a guard. |
| 3 | Downward-glide factor is `-0.1` | It is `movement.y * -0.1 * liftForce` — multiplied by `cos²(lean)`. **Verify our implementation carries the `liftForce` factor; the reference does not document it.** |
| 4 | Collision damage is `ceil(10·Δv − 3)` | `(float)(diff * 10.0 - 3.0)`, no rounding, applied only when `horizontalCollision` is set, and only server-side. |

## Fidelity traps

These are the details that make a transcribed formula produce visibly correct flight and measurably
wrong numbers. All three would pass a formula review and fail a trace comparison.

**1. The drag constants are `float` literals widened to `double`.** `Vec3.multiply` takes three
`double` parameters, so `multiply(0.99F, 0.98F, 0.99F)` passes:

```
0.99F as double = 0.9900000095367432
0.98F as double = 0.9800000190734863
```

Writing `0.99` and `0.98` as double literals introduces a relative error of `9.5e-9` **per tick**, in
the one operation that touches every component every tick. Over a 200-tick trace that compounds well
past the `1e-6` per-tick tolerance the design sets.

**2. The lean angle is computed in `float`.** `getXRot()` is `float`, `Mth.DEG_TO_RAD` is
`(float)(Math.PI / 180.0)`, and the multiplication happens in `float` before anything widens. For a
pitch of `-12.5°`:

```
float:  -0.21816616
double: -0.2181661564992912
```

`Math.toRadians(pitch)` is the wrong conversion here.

**3. The trigonometry is mixed, and one half is a lookup table.** The lift term uses
`Math.cos(leanAngle)` — a true double cosine. The pitch-boost term uses `Mth.sin(leanAngle)`, and
`Entity.calculateViewVector` uses both `Mth.sin` and `Mth.cos`. `net/minecraft/util/Mth.java`
contains **exactly these two methods** on this path, and no `float` overload of either:

```java
private static final float[] SIN = Util.make(new float[65536], sin -> {
    for (int i = 0; i < sin.length; i++) {
        sin[i] = (float)Math.sin(i / 10430.378350470453);
    }
});

public static float sin(final double i) {
    return SIN[(int)((long)(i * 10430.378350470453) & 65535L)];
}

public static float cos(final double i) {
    return SIN[(int)((long)(i * 10430.378350470453 + 16384.0) & 65535L)];
}
```

That is a 65536-entry table returning `float`, with quantisation error orders of magnitude larger
than the double `Math.sin` it superficially resembles. Reimplementing both terms with `Math.*` — the
obvious, tidy choice — silently changes the climb behaviour. The port must use a table with the same
size and index arithmetic.

Two details of that index arithmetic are easy to get wrong, and neither shows up in a plot:

- **There is no `float` overload in 26.2.** `calculateViewVector` passes `float` arguments
  (`xRot * Mth.DEG_TO_RAD`), and they **widen to `double`** and bind to the methods above. A port
  that assumes the "classic float form" — `SIN[(int)(i * 10430.378F + 16384.0F) & 65535]`, which
  older Minecraft versions did carry — picks a different table entry for roughly 0.1% of angles. At
  `-1.56955` rad the double form lands on index 12 and the float form on index 13, whose entries are
  `9.59e-5` apart: a full table step, a hundred times the `1e-6` per-tick parity threshold.
- **`cos` is `sin` shifted by a quarter turn of the table**, `+16384.0` added *before* the `long`
  truncation — not `Math.cos` and not `sin(i + PI/2)`.

## Answered: does `air_drag_modifier` affect elytra drag?

**No.** The risk register carried this as unresolved and the research could only rate it
`T6-speculative`. `updateFallFlyingMovement` ends in a literal `multiply(0.99F, 0.98F, 0.99F)` with
no attribute lookup on that path. The 26.2 attribute does not reach elytra flight.

The gravity attribute **does** reach it, through `getEffectiveGravity()`:

```java
protected double getEffectiveGravity() {
    boolean isFalling = this.getDeltaMovement().y <= 0.0;
    return isFalling && this.hasEffect(MobEffects.SLOW_FALLING) ? Math.min(this.getGravity(), 0.01) : this.getGravity();
}
```

Slow Falling while descending clamps gravity to `0.01`, which changes elytra flight substantially. If
the game ever grants that effect, the simulation must model it.

## Steps 9 and 10, which the current implementation omits

`travelFallFlying` calls `this.move(MoverType.SELF, this.getDeltaMovement())` — position integration
and block collision — and then evaluates collision damage from the horizontal speed lost during that
move. The plausibility check specified for the rebuild needs both, because predicting where a client
should be requires integrating and colliding, not only advancing velocity.

Two further behaviours belong to the same method:

- `onClimbable()` ends gliding immediately and falls through to normal air travel.
- `stopFallFlying()` sets the shared flag to `true` and then to `false`, which forces a metadata
  update rather than expressing an intent.

## The collision path, as it actually reads

`travelFallFlying`'s `this.move(MoverType.SELF, ...)` is the second half of the tick, and the parity
suite measures it as directly as it measures the velocity update. Source of record:
`net/minecraft/world/entity/Entity.java` (`move`, `collide`, `collideWithShapes`,
`restituteMovementAfterCollisions`, `getEntityBounciness`), `net/minecraft/core/Direction.java`
(`axisStepOrder`), `net/minecraft/world/phys/shapes/Shapes.java` (`collide`) and
`net/minecraft/world/phys/shapes/VoxelShape.java` (`collide`, `collideX`).

**There is no `AABB.collideX/collideY/collideZ` in 26.2.** Earlier versions carried per-axis clamps on
`AABB`; 26.2 clamps against `VoxelShape` instead, reached through `Shapes.collide`. Code citing the
`AABB` names is citing a method that no longer exists.

### Axis order

`net/minecraft/core/Direction.java:379`:

```java
public static ImmutableList<Direction.Axis> axisStepOrder(final Vec3 movement) {
    return Math.abs(movement.x) < Math.abs(movement.z) ? YZX_AXIS_ORDER : YXZ_AXIS_ORDER;
}
```

with (`:48-49`) `YXZ_AXIS_ORDER = [Y, X, Z]` and `YZX_AXIS_ORDER = [Y, Z, X]`. Y always resolves
first; whichever of X or Z has the larger magnitude resolves next. The comparison is strict, so an
exact tie (`|dx| == |dz|`, including `0 == 0`) takes the `YXZ` branch.

### The axis-separated sweep

`net/minecraft/world/entity/Entity.java:1245`:

```java
private static Vec3 collideWithShapes(final Vec3 movement, final AABB boundingBox, final List<VoxelShape> shapes) {
    if (shapes.isEmpty()) {
        return movement;
    }

    Vec3 resolvedMovement = Vec3.ZERO;

    for (Direction.Axis axis : Direction.axisStepOrder(movement)) {
        double axisMovement = movement.get(axis);
        if (axisMovement != 0.0) {
            double collision = Shapes.collide(axis, boundingBox.move(resolvedMovement), shapes, axisMovement);
            resolvedMovement = resolvedMovement.with(axis, collision);
        }
    }

    return resolvedMovement;
}
```

Each axis is clamped against the box already moved by the axes resolved before it
(`boundingBox.move(resolvedMovement)`), and the same `shapes` list — collected once, from
`boundingBox.expandTowards(movement)` in `collideBoundingBox` — serves all three. An axis whose
component is exactly `0.0` is skipped and stays at the `Vec3.ZERO` initialiser.

`Entity.collide` (`:1142`) wraps this with the step-up branch, gated on
`maxUpStep() > 0.0F && (onGroundAfterCollision || onGround()) && (xCollision || zCollision)`, and
short-circuits `movement.lengthSqr() == 0.0` before ever collecting colliders.

### The per-axis clamp

`net/minecraft/world/phys/shapes/Shapes.java:234`:

```java
public static double collide(final Direction.Axis axis, final AABB moving, final Iterable<VoxelShape> shapes, double distance) {
    for (VoxelShape shape : shapes) {
        if (Math.abs(distance) < 1.0E-7) {
            return 0.0;
        }

        distance = shape.collide(axis, moving, distance);
    }

    return distance;
}
```

`net/minecraft/world/phys/shapes/VoxelShape.java:252`:

```java
public double collide(final Direction.Axis axis, final AABB moving, final double distance) {
    return this.collideX(AxisCycle.between(axis, Direction.Axis.X), moving, distance);
}

protected double collideX(final AxisCycle transform, final AABB moving, double distance) {
    if (this.isEmpty()) {
        return distance;
    }

    if (Math.abs(distance) < 1.0E-7) {
        return 0.0;
    }

    AxisCycle inverse = transform.inverse();
    Direction.Axis aAxis = inverse.cycle(Direction.Axis.X);
    Direction.Axis bAxis = inverse.cycle(Direction.Axis.Y);
    Direction.Axis cAxis = inverse.cycle(Direction.Axis.Z);
    double maxA = moving.max(aAxis);
    double minA = moving.min(aAxis);
    int aMin = this.findIndex(aAxis, minA + 1.0E-7);
    int aMax = this.findIndex(aAxis, maxA - 1.0E-7);
    int bMin = Math.max(0, this.findIndex(bAxis, moving.min(bAxis) + 1.0E-7));
    int bMax = Math.min(this.shape.getSize(bAxis), this.findIndex(bAxis, moving.max(bAxis) - 1.0E-7) + 1);
    int cMin = Math.max(0, this.findIndex(cAxis, moving.min(cAxis) + 1.0E-7));
    int cMax = Math.min(this.shape.getSize(cAxis), this.findIndex(cAxis, moving.max(cAxis) - 1.0E-7) + 1);
    int aSize = this.shape.getSize(aAxis);
    if (distance > 0.0) {
        for (int a = aMax + 1; a < aSize; a++) {
            for (int b = bMin; b < bMax; b++) {
                for (int c = cMin; c < cMax; c++) {
                    if (this.shape.isFullWide(inverse, a, b, c)) {
                        double newDistance = this.get(aAxis, a) - maxA;
                        if (newDistance >= -1.0E-7) {
                            distance = Math.min(distance, newDistance);
                        }

                        return distance;
                    }
                }
            }
        }
    } else if (distance < 0.0) {
        for (int a = aMin - 1; a >= 0; a--) {
            for (int b = bMin; b < bMax; b++) {
                for (int c = cMin; c < cMax; c++) {
                    if (this.shape.isFullWide(inverse, a, b, c)) {
                        double newDistance = this.get(aAxis, a + 1) - minA;
                        if (newDistance <= 1.0E-7) {
                            distance = Math.max(distance, newDistance);
                        }

                        return distance;
                    }
                }
            }
        }
    }

    return distance;
}
```

For a full unit cube — the only shape the rebuild's `CollisionSpace` exposes — this reduces to: if
the moving box overlaps the cube on the other two axes, clamp the movement to the near face of the
cube in the direction of travel (`other.min - box.max` moving positive, `other.max - box.min` moving
negative), keeping whichever of that limit and the requested distance is smaller in magnitude. The
`± 1.0E-7` on the `findIndex` calls shrinks the moving box by that amount on the perpendicular axes
before the overlap test, which is the `VoxelShape` equivalent of the strict comparisons in
`AABB.intersects` documented below.

**Finding — Vanilla snaps a sub-`1.0E-7` residual to exactly zero; the rebuild's `MovementResolver`
passes it through.** Both `Shapes.collide:236` and `VoxelShape.collideX:261` return `0.0` outright
when `Math.abs(distance) < 1.0E-7`, before any shape is consulted. Note the position of the check in
`Shapes.collide`: it sits *inside* the loop, so an empty shape list returns the distance unchanged —
the snap only happens when there is at least one shape in range. `MovementResolver` has no
equivalent, so a movement of, say, `5e-8` against a nearby block travels `5e-8` in the rebuild and
`0.0` in Vanilla. This is recorded rather than fixed: it changes behaviour, and calibrating it
belongs with the real traces.

### Restitution

`net/minecraft/world/entity/Entity.java:760` computes the flags:

```java
boolean xCollision = !Mth.equal(delta.x, movement.x);
boolean zCollision = !Mth.equal(delta.z, movement.z);
this.horizontalCollision = xCollision || zCollision;
boolean movedVertically = Math.abs(delta.y) > 0.0;
if (movedVertically || this.isLocalInstanceAuthoritative()) {
    this.verticalCollision = delta.y != movement.y;
    this.verticalCollisionBelow = this.verticalCollision && delta.y < 0.0;
    this.setOnGroundWithMovement(this.verticalCollisionBelow, this.horizontalCollision, movement);
}
```

with `Mth.equal(double, double)` (`net/minecraft/util/Mth.java:162`) being
`Math.abs(b - a) < 1.0E-5F`. Restitution runs (`:785-787`) only when

```java
if (this.canSimulateMovement() && (movedVertically && this.verticalCollision || this.horizontalCollision)) {
    this.restituteMovementAfterCollisions(effectState, xCollision, zCollision, movement);
}
```

and reads (`:803`):

```java
private void restituteMovementAfterCollisions(final BlockState effectState, final boolean xCollision, final boolean zCollision, final Vec3 movement) {
    double restitution = this.isSuppressingBounce() ? 0.0 : this.getEntityBounciness();
    Vec3 currentMovement = this.getDeltaMovement();
    Vec3 movementAfterBounce = currentMovement;
    if (xCollision) {
        movementAfterBounce = movementAfterBounce.with(Direction.Axis.X, -currentMovement.x * restitution);
    }

    if (zCollision) {
        movementAfterBounce = movementAfterBounce.with(Direction.Axis.Z, -currentMovement.z * restitution);
    }

    boolean bounced = restitution > 0.0 && (xCollision || zCollision);
    if (this.verticalCollision) {
        if (this.verticalCollisionBelow) {
            restitution = !(-currentMovement.y < this.getEffectiveGravity()) && !this.isSuppressingBounce() && !effectState.is(BlockTags.SUPPRESSES_BOUNCE)
                ? Math.max(restitution, this.getBlockBounciness(effectState.getBlock()))
                : 0.0;
        }

        double gravityCompensation;
        double effectiveDrag;
        if (restitution > 0.0) {
            double portionWithMovement = movement.y / currentMovement.y;
            gravityCompensation = portionWithMovement * this.getEffectiveGravity();
            effectiveDrag = Mth.lerp(portionWithMovement, 1.0, this.getAirDrag());
            bounced = true;
        } else {
            gravityCompensation = 0.0;
            effectiveDrag = 1.0;
        }

        movementAfterBounce = movementAfterBounce.with(Direction.Axis.Y, (gravityCompensation - currentMovement.y) * effectiveDrag * restitution);
    }

    if (bounced) {
        this.gameEvent(GameEvent.BOUNCE);
        this.syncPosition = true;
    }

    this.setDeltaMovement(movementAfterBounce);
}
```

`getEntityBounciness()` (`:855`) is `return 0.0;` for every entity, and `getBlockBounciness` is
non-zero only for slime and beds. At `restitution = 0.0` every branch collapses: the X and Z terms
become `-v * 0.0 = 0.0`, and the Y term becomes `(0.0 - v.y) * 1.0 * 0.0 = 0.0`. A collided axis'
velocity therefore becomes exactly `0.0`, not a fraction of itself — which is what the rebuild
implements.

**Horizontal restitution gates on the *tolerant* flags; only the vertical flag is exact.** The
`xCollision` and `zCollision` handed to `restituteMovementAfterCollisions` at `:786` are the very
same locals computed at `:760-761` as `!Mth.equal(...)` — there is no second, exact per-axis pair
anywhere in `Entity.move`. They are therefore `false` whenever the clamp moved that component by
less than `1.0E-5F`, and a horizontal clamp inside `(0, 1.0E-5)` is not a collision for any purpose:
`horizontalCollision` stays `false`, no restitution runs, and the velocity on that axis survives
even though the *position* was clamped. Only `verticalCollision` is exact (`delta.y != movement.y`,
`:762`).

The rebuild followed this until commit `dcfe98e`, which split `MovementResult` into an exact
per-axis pair for restitution to read and a tolerant `horizontalCollision` for reporting. That split
was wrong and has been reverted: `MovementResult.xCollision()` and `zCollision()` are now the
tolerant flags, `verticalCollision()` stays exact, and `horizontalCollision()` is derived as
`xCollision || zCollision` exactly as Vanilla derives it. `ElytraSimulator.restitute` reads the same
three flags Vanilla's restitution reads.

Pinned by `MovementResolverTest.aSubToleranceClampReportsNoAxisCollisionEither` (a `5e-6` clamp
reports neither flag) and `ElytraSimulatorTest.restitutionPreservesVelocityOnASubToleranceHorizontalClamp`
(the same clamp end to end: position moved, velocity untouched).

### The firework rocket impulse

`net/minecraft/world/entity/projectile/FireworkRocketEntity.java:127-142`, applied to the attached
entity from the rocket's own `tick()` — outside `updateFallFlyingMovement`, so the velocity the
update reads already includes that tick's impulse:

```java
if (this.attachedToEntity.isFallFlying()) {
    Vec3 lookAngle = this.attachedToEntity.getLookAngle();
    double power = 1.5;
    double powerAdd = 0.1;
    Vec3 movement = this.attachedToEntity.getDeltaMovement();
    this.attachedToEntity
        .setDeltaMovement(
            movement.add(
                lookAngle.x * 0.1 + (lookAngle.x * 1.5 - movement.x) * 0.5,
                lookAngle.y * 0.1 + (lookAngle.y * 1.5 - movement.y) * 0.5,
                lookAngle.z * 0.1 + (lookAngle.z * 1.5 - movement.z) * 0.5
            )
        );
}
```

`docs/reference/firework-boost.md` documents the legacy Minestom boost system, whose impulse is a
different formula entirely. That document is not a description of Vanilla and must not be used as
one; this block is.

### The view vector

`net/minecraft/world/entity/Entity.java:1967`, the source of the `lookAngle` every branch above reads
(`getLookAngle()` is `calculateViewVector(getXRot(), getYRot())`, `:2576`):

```java
public final Vec3 calculateViewVector(final float xRot, final float yRot) {
    float realXRot = xRot * Mth.DEG_TO_RAD;
    float realYRot = -yRot * Mth.DEG_TO_RAD;
    float yCos = Mth.cos(realYRot);
    float ySin = Mth.sin(realYRot);
    float xCos = Mth.cos(realXRot);
    float xSin = Mth.sin(realXRot);
    return new Vec3(ySin * xCos, -xSin, yCos * xCos);
}
```

Both conversions are the `float` multiplication of fidelity trap 2, and all four trigonometric calls
go through the `Mth` table of trap 3.

## Bounding-box intersection is strict on all three axes

`net/minecraft/world/phys/AABB.java:235`:

```java
public boolean intersects(final double minX, final double minY, final double minZ, final double maxX, final double maxY, final double maxZ) {
    return this.minX < maxX && this.maxX > minX && this.minY < maxY && this.maxY > minY && this.minZ < maxZ && this.maxZ > minZ;
}
```

Every comparison is strict. Two boxes that share a face, an edge or a corner do **not** intersect —
a box occupying `[0,1]` on x does not intersect one occupying `[1,2]`. This matters for the
rebuild's `Aabb.intersects`, which used `<=` / `>=` and claimed Vanilla parity it did not have: with
non-strict comparisons a player resting exactly on a block boundary registers a collision Vanilla
would not, so any plausibility check calibrated against a real client drifts at exactly the positions
clients most often occupy.

`AABB.intersects(BlockPos)` expands the position to `[x, x+1]` per axis before applying the same
predicate, so block collision inherits the strict semantics unchanged.
