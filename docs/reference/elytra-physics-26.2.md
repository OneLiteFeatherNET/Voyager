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

**Vanilla snaps a sub-`1.0E-7` remaining distance to exactly zero, and the guard's position is the
whole of its behaviour.** `Shapes.collide:236` returns `0.0` when `Math.abs(distance) < 1.0E-7`, but
the check sits *inside* the loop, evaluated once per shape against the distance as clamped so far.
Two consequences follow, and they pull in opposite directions:

- With an **empty** shape list the loop body never runs, so even a `5e-8` movement passes through
  untouched. (`collideWithShapes:1246` short-circuits an empty list before this anyway.) This is not
  a blanket "snap small movements to zero"; reading it as one — hoisting the check above the loop —
  would freeze an entity drifting through open air.
- Once an earlier shape has clamped the remaining distance under `1.0E-7`, the **next** shape makes
  the result exactly `0.0`, not the residual. Because the guard runs before `shape.collide`, and the
  perpendicular-axis overlap test lives *inside* `shape.collide`
  (`VoxelShape.collideX:263-268`, the `findIndex` calls), any remaining shape triggers it — including
  one the clamp would otherwise have skipped for not overlapping on this axis.

`VoxelShape.collideX:261` repeats the same guard at the top of `shape.collide`. Against a distance
the outer guard has already accepted it can never fire, so it is redundant.

`MovementResolver`'s three per-axis clamps now carry this guard in the same position — at the top of
the candidate loop, before the overlap test. Pinned by
`MovementResolverTest.aSubEpsilonMovementWithNoCandidatesPassesThroughUnchanged` and
`aClampLeavingLessThanTheSnapEpsilonReturnsExactlyZero`.

**Which flag does the snap report?** The source settles it, and the answer is that the snap is never
visible to the flags in its own right. `Entity.move:760-762` compares the requested movement against
the final one and nothing in between, so what the flags see is `|requested - 0.0|`:

- A snap after a large clamp (requested `1.0`, clamped to `5e-8`, snapped to `0.0`) is `1.0` away
  from the request, well over `Mth.equal`'s `1.0E-5F` — an ordinary horizontal collision.
- A snap of a movement that was *itself* under `1.0E-5` (requested `5e-6`, snapped to `0.0`) is
  `Mth.equal` to the request, so **no** horizontal collision is reported even though a block stopped
  the movement dead. Pinned by
  `MovementResolverTest.aSnapToZeroBelowTheHorizontalToleranceReportsNoCollision`.
- The same case on Y *is* reported, `verticalCollision` being exact (`delta.y != movement.y`). Note
  that `verticalCollisionBelow` reads `delta.y < 0.0` — `delta` is the *requested* movement here,
  `movement` the collided one (`:786` passes the collided vector to `restituteMovementAfterCollisions`,
  and `setPos` is fed `pos.add(movement)`) — so a snapped-to-zero descent still counts as landing.

The `1.0E-7` guard therefore never needs a flag of its own; it is a clamp like any other.

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
entity from the rocket's own `tick()` — outside `updateFallFlyingMovement`, **on the far side of it**:

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

**Where in the tick it lands, measured.** A rocket is an entity, spawned after the glider it is
attached to, and `EntityTickList` is insertion-ordered — so on any given level tick the glider runs
`updateFallFlyingMovement` and `move` *first*, and the rocket's impulse then lands on the velocity
the glider carries into the **next** tick. The consequences for a port:

- The position reached on a boosted tick is computed from the velocity the glider **entered** that
  tick with. Applying the impulse before the steps moves the position instead, and the E2a fixtures
  separate the two outright: boost-first missed `single-boost`'s first boosted tick by `0.69` blocks
  and stayed `1.1e-02` out for the rest of the burn, boost-last reproduces every boosted tick's
  position bit-for-bit.
- The two orders apply the same *number* of impulses over a burn, so a free-running replay merely
  looks scaled and a converged burn looks almost right. Only a one-step residual tells them apart.
  This is why an earlier revision of the port read "outside `updateFallFlyingMovement`" as "before
  it" and passed review.
- It lands after `Entity.move`, so after collision restitution: a collided axis is zeroed and *then*
  boosted.

**The impulse is a blend, not an addition.** Read as a whole, `v' = 0.5 · v + 0.85 · lookAngle`,
which is the halfway point between the current velocity and a terminal `1.7 · lookAngle`. So a burn
converges instead of accelerating without bound — `single-boost` settles at `velZ = 1.6716` and holds
it to the last bit — and two rockets burning at once converge *nearer* the terminal value rather than
doubling the speed: `chained-boosts` records a steady `1.6862`.

**The impulse is summed before being added, once.** It is the whole argument to `Vec3.add`, so
`v + (look.x * 0.1 + (look.x * 1.5 - v.x) * 0.5)`. Writing it without the parentheses associates left
to right and rounds twice against `v`; the difference is a bit or two, which passes a `1e-12`
tolerance and is enough to stop `single-boost` from being bit-exact.

`docs/reference/firework-boost.md` documents the legacy Minestom boost system, whose impulse is a
different formula entirely. That document is not a description of Vanilla and must not be used as
one; this block is.

### `LivingEntity.aiStep`'s 0.003 movement deadzone

`net/minecraft/world/entity/LivingEntity.java`, in `aiStep()` — **before** `travel` and therefore
before `travelFallFlying` and every step above:

```java
Vec3 movement = this.getDeltaMovement();
double x = movement.x;
double y = movement.y;
double z = movement.z;
if (Math.abs(movement.x) < 0.003) { x = 0.0; }
if (Math.abs(movement.y) < 0.003) { y = 0.0; }
if (Math.abs(movement.z) < 0.003) { z = 0.0; }
this.setDeltaMovement(x, y, z);
```

Per axis, against the incoming component, and blind to the vector's length: `(0.002, 0, 0.002)` is
zeroed on both axes even though its horizontal length exceeds `0.003`.

**It is not a rounding tidy-up.** `0.003` is three orders of magnitude above the bounds this module
is measured against, and clamping a component changes which branches of
`updateFallFlyingMovement` fire on that tick: a `y` of `-0.002` becomes `0.0`, which closes the
sink-conversion branch's `movement.y < 0.0` guard, and a clamped `x` or `z` changes `moveHorLength`
for every branch after it. Its effect therefore concentrates exactly where a component crosses zero —
at manoeuvre transitions. Omitting it left `sustained-turn`, `pitch-extremes` and `dive-and-pull-out`
each with a burst of `2–3e-03` deviations around their transition plus a lone outlier elsewhere, and
reinstating it made all three bit-exact.

### The entity box is assembled in `float`

`net/minecraft/world/entity/EntityDimensions.java`:

```java
public AABB makeBoundingBox(final double x, final double y, final double z) {
    float f = this.width() / 2.0F;
    float g = this.height();
    return new AABB(x - (double) f, y, z - (double) f, x + (double) f, y + (double) g, z + (double) f);
}
```

`width()` and `height()` are `float`, and the halving happens in `float` before the widening cast. A
`0.6F`-wide entity therefore has a half-width of `0.300000011920928955078125`, **not** `0.3` —
`1.19e-08` higher — and a `1.8F`-tall one a box height of `1.7999999523162842`. This is fidelity
trap 2 applied to the box rather than to the formula, and it decides a resting position exactly:
`wall-graze` records its final `z` as `89.699999988079071`, not `89.7`, and a `double` `0.3`
reproduces that fixture's velocity bit-for-bit while missing its position by exactly that
`1.19e-08` — the whole of the residual left in it once the boost order was corrected.

Only the width is pinned by a recording: `wall-graze`'s wall spans `y = 250..310` and `landing`'s
floor is under the feet, so no fixture brings the top of the box into contact with anything. The E2a
fixtures were also recorded from a zombie (`0.6F × 1.95F`), so a future ceiling-contact fixture would
have to be recorded from the entity the game actually flies before it could pin a player's `1.8F`.

## Measured parity (E2b Task 7)

The nine E2a fixtures, replayed against the port. **Both bounds are exactly zero.** The design spec
carried `1e-6` per tick and `0.01` cumulative as stated assumptions; measurement replaced them.

Two forms are measured, and only the first calibrates:

- **One-step residual** — from the recorded state at tick *k*, advance exactly one tick, compare
  against the recorded state at *k+1*. Measures the formula alone.
- **Free-running replay** — seed once from tick 0 and never correct. Measures the seeding *and* the
  formula, and turns a single slip into a long decaying tail: while the recorder's first-tick
  transient was unfixed, a fixture that is bit-exact step by step still accumulated `8.2e-02` of
  free-running drift. Kept under its own bound, never as the only measurement.

| Profile | ticks | one-step position | one-step velocity | free-running drift |
|---|---|---|---|---|
| `steady-glide` | 200 | `0` (199/199) | `0` | `0` |
| `climb-into-stall` | 200 | `0` (199/199) | `0` | `0` |
| `sustained-turn` | 200 | `0` (199/199) | `0` | `0` |
| `pitch-extremes` | 200 | `0` (199/199) | `0` | `0` |
| `dive-and-pull-out` | 200 | `0` (199/199) | `0` | `0` |
| `single-boost` | 200 | `0` (199/199) | `0` | `0` |
| `chained-boosts` | 200 | `0` (199/199) | `0` on 170/199 ticks; **not comparable** on ticks 20–48 | **not comparable** (`5.0e-01`) |
| `wall-graze` | 220 | `0` (219/219) | `0` | `0` |
| `landing` | 200 | `0` (138/138 up to touchdown) | `0` | `0` |

`onGround` matches on every comparable tick of every profile too, touchdown included.

**Bound chosen: `0`, margin `0`.** There is nothing to take a margin from. A non-zero bound here
would not buy safety, it would spend it: at `1e-6` the port could drop `aiStep`'s deadzone and still
pass most profiles, and the `float` half-width would be invisible at any bound above `1.19e-08`. Both
were real defects and both were found by the bound being zero.

**Residual risk, not yet measured.** `Math.cos` (which `liftForce` uses, and which Vanilla uses too)
is specified to 1 ulp rather than bit-exactly, so a CI runner on another architecture could show a
last-bit residual. If that happens, the response is to record the measurement and state the new
bound — not to widen it pre-emptively, and not to substitute `StrictMath`, which would diverge from
Vanilla on whichever platform the two disagree.

### What is deliberately not compared, and why neither is a tolerance

**`landing` after touchdown.** `LivingEntity.updateFallFlying` ends the glide the moment the entity
is on the ground, so from the tick after touchdown the recording is ordinary ground movement, not
`travelFallFlying` — its `velY` is a constant `-0.078400002` (`-0.08 * 0.98`, the non-flying
gravity-and-drag step) and its `velZ` decays with ground friction, roughly halving per tick. This
module ports `travelFallFlying` and deliberately not `stopFallFlying` or the branch it hands over to,
so those 61 ticks lie outside anything a threshold could describe. No bound loose enough to pass them
would still catch a real defect. The touchdown tick itself is compared like every other — clamp to
the block face, zeroed `velY`, `onGround` flag — and matches bit-for-bit. Derived from the fixture
(first tick with `onGround`), not hardcoded.

**`chained-boosts`'s burn, on velocity only.** A recording defect, and the one finding of this task
that belongs to E2a rather than to the port. `TraceTick` carries a `boolean fireworkBoostActive` and
a single `fireworkTicksRemaining` that `GliderRunner.sampleGlider` computes as the **maximum** over
every attached rocket, so one rocket and three record identically — while Vanilla applies one impulse
per rocket per tick. `chained-boosts` exists precisely to fly a second ignition into a live burn, and
its recorded steady `velZ` of `1.686154` against `single-boost`'s `1.671612` is the two-rocket
signature: the fixture shows the effect and cannot say what produced it. The replay applies one
impulse where Vanilla applied two, and the `7.5e-03` that leaves is in the recording, not in the port.
A second ignition is at least *visible*, because the field is a maximum and a fresh rocket outlives
the burning one: inside a flagged run the value otherwise falls by exactly one per tick, so any
increase is a new rocket, and the run it appears in is treated as unaccountable in full.

**The fix belongs in the recorder**: sample the number of attached rockets per tick and bump
`formatVersion`. Until a re-recording exists, those 29 ticks are compared on position only — position
is insensitive to the impulse count, since a tick's position follows from the velocity the glider
*entered* it with. `single-boost`'s burn is accountable in full and is compared on velocity tick by
tick, so the impulse formula itself is not left unpinned.

**One more recorder artefact, compensated rather than excluded.** The boost flag is one tick short at
the trailing edge of a burn: the sample for tick *n* is read from a `runTaskLater(…, 1L)` callback,
CraftBukkit runs its scheduler heartbeat at the top of `tickChildren` before the levels tick, so the
sample is taken at the start of tick *n+1* — by which point a rocket that boosted during tick *n*
and detonated at the end of it has already been filtered out of `activeFireworks`. `single-boost`
flags ticks `30..51` while tick `52`'s recorded velocity (`1.671612598`, above tick 51's
`1.671612475`, where an unboosted tick would have fallen to `1.6497`) shows the impulse still ran. The
replay therefore reads the impulse as having run during a tick whenever the flag is set at that tick
**or at the one before it**. That is derived from the recorder's code, not fitted to the residual.

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
