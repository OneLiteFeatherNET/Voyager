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
`Math.cos(leanAngle)` — a true double cosine. The pitch-boost term uses `Mth.sin(leanAngle)`:

```java
public static float sin(final double i) {
    return SIN[(int)((long)(i * 10430.378350470453) & 65535L)];
}
```

That is a 65536-entry table returning `float`, with quantisation error orders of magnitude larger
than the double `Math.sin` it superficially resembles. Reimplementing both terms with `Math.*` — the
obvious, tidy choice — silently changes the climb behaviour. The port must use a table with the same
size and index arithmetic.

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
