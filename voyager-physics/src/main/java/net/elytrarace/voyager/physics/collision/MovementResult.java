package net.elytrarace.voyager.physics.collision;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.physics.exception.InvalidSimulationStateException;

/**
 * The outcome of resolving one attempted movement of a box against the world: how far it actually
 * moved after collision, and what it touched along the way.
 *
 * <p>Two different notions of "did this axis collide" are carried, deliberately: {@link
 * #xCollision}, {@link #verticalCollision} and {@link #zCollision} are exact — {@code true}
 * whenever the resolver's clamp changed the requested component at all, with no tolerance — while
 * {@link #horizontalCollision} keeps Vanilla's {@code Mth.equal} tolerance of {@code 1.0E-5F}. A
 * caller zeroing velocity after a collision reads the exact fields; a caller merely reporting
 * whether a collision happened for gameplay purposes reads {@link #horizontalCollision} (Vanilla's
 * block-damage check reads a still-different, distance-based delta).
 *
 * <p>Vanilla itself splits them differently, and the difference is deliberate here rather than
 * accidental: {@code Entity.move} computes its vertical flag exactly ({@code delta.y != movement.y})
 * but both horizontal flags through {@code Mth.equal}, and hands those tolerant flags to
 * {@code Entity.restituteMovementAfterCollisions}. On a clamp inside {@code (0, 1.0E-5)} Vanilla
 * therefore keeps the horizontal velocity while this port's caller zeroes it. Recorded as a finding
 * in the collision-path section of {@code docs/reference/elytra-physics-26.2.md}; resolving it is a
 * behaviour change that belongs with the real traces.
 *
 * @param allowedMovement the movement actually applied, after any axis was clamped by a collision
 * @param xCollision {@code true} when the X component was clamped at all, with no tolerance
 * @param verticalCollision {@code true} when the Y component was clamped at all, with no tolerance,
 *     in either direction — not only while landing; see {@link #onGround} for the landing-specific
 *     flag
 * @param zCollision {@code true} when the Z component was clamped at all, with no tolerance
 * @param horizontalCollision {@code true} when the X or the Z component was clamped by more than
 *     Vanilla's {@code 1.0E-5F} tolerance
 * @param onGround {@code true} when the Y component was clamped while moving downwards
 */
public record MovementResult(
        Vec3 allowedMovement,
        boolean xCollision,
        boolean verticalCollision,
        boolean zCollision,
        boolean horizontalCollision,
        boolean onGround) {

    public MovementResult {
        if (!Double.isFinite(allowedMovement.x())
                || !Double.isFinite(allowedMovement.y())
                || !Double.isFinite(allowedMovement.z())) {
            throw new InvalidSimulationStateException(
                    "allowedMovement must be finite, was %s".formatted(allowedMovement));
        }
    }
}
