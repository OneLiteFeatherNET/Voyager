package net.elytrarace.voyager.physics.collision;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.physics.exception.InvalidSimulationStateException;

/**
 * The outcome of resolving one attempted movement of a box against the world: how far it actually
 * moved after collision, and what it touched along the way.
 *
 * <p>The two horizontal flags carry Vanilla's {@code Mth.equal} tolerance of {@code 1.0E-5F}; the
 * vertical flag does not. That split is Vanilla's own, transcribed from {@code Entity.move}
 * ({@code net/minecraft/world/entity/Entity.java:760-765}):
 *
 * <pre>{@code
 * boolean xCollision = !Mth.equal(delta.x, movement.x);
 * boolean zCollision = !Mth.equal(delta.z, movement.z);
 * this.horizontalCollision = xCollision || zCollision;
 * ...
 * this.verticalCollision = delta.y != movement.y;
 * }</pre>
 *
 * <p>and those <em>same</em> tolerant flags are what {@code Entity.move:786} hands to
 * {@code restituteMovementAfterCollisions}. There is therefore only one notion of "did this
 * horizontal axis collide" — a clamp inside {@code (0, 1.0E-5)} counts as no collision for every
 * purpose, restitution included, and the velocity survives it. An earlier revision of this record
 * carried a second, exact pair of horizontal flags for restitution to read; they were removed once
 * the source settled that Vanilla has no such pair.
 *
 * <p>{@link #horizontalCollision()} is derived rather than stored, exactly as Vanilla derives it.
 *
 * @param allowedMovement the movement actually applied, after any axis was clamped by a collision
 * @param xCollision {@code true} when the X component was clamped by at least Vanilla's
 *     {@code 1.0E-5F} tolerance
 * @param verticalCollision {@code true} when the Y component was clamped at all, with no tolerance,
 *     in either direction — not only while landing; see {@link #onGround} for the landing-specific
 *     flag
 * @param zCollision {@code true} when the Z component was clamped by at least Vanilla's
 *     {@code 1.0E-5F} tolerance
 * @param onGround {@code true} when the Y component was clamped while moving downwards
 */
public record MovementResult(
        Vec3 allowedMovement,
        boolean xCollision,
        boolean verticalCollision,
        boolean zCollision,
        boolean onGround) {

    public MovementResult {
        if (!Double.isFinite(allowedMovement.x())
                || !Double.isFinite(allowedMovement.y())
                || !Double.isFinite(allowedMovement.z())) {
            throw new InvalidSimulationStateException(
                    "allowedMovement must be finite, was %s".formatted(allowedMovement));
        }
    }

    /**
     * {@code true} when either horizontal axis collided — Vanilla's
     * {@code this.horizontalCollision = xCollision || zCollision}. This is the flag Vanilla's
     * collision-damage check ({@code handleFallFlyingCollisions}) is gated on.
     */
    public boolean horizontalCollision() {
        return xCollision || zCollision;
    }
}
