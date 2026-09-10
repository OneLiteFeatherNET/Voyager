package net.elytrarace.voyager.physics.collision;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.physics.exception.InvalidSimulationStateException;

/**
 * The outcome of resolving one attempted movement of a box against the world: how far it actually
 * moved after collision, and what it touched along the way.
 *
 * @param allowedMovement the movement actually applied, after any axis was clamped by a collision
 * @param horizontalCollision {@code true} when the X or the Z component was clamped
 * @param onGround {@code true} when the Y component was clamped while moving downwards
 */
public record MovementResult(Vec3 allowedMovement, boolean horizontalCollision, boolean onGround) {

    public MovementResult {
        if (!Double.isFinite(allowedMovement.x())
                || !Double.isFinite(allowedMovement.y())
                || !Double.isFinite(allowedMovement.z())) {
            throw new InvalidSimulationStateException(
                    "allowedMovement must be finite, was %s".formatted(allowedMovement));
        }
    }
}
