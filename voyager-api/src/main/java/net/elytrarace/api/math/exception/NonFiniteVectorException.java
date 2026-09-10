package net.elytrarace.api.math.exception;

/**
 * Thrown when a vector component is NaN or infinite.
 *
 * <p>This exists so a non-finite value can never reach the platform layer. Minestom permanently
 * disables a player's velocity channel once a NaN is passed to {@code setVelocity}, including for
 * every later valid call, so the invariant is enforced at construction rather than at the boundary.
 */
public final class NonFiniteVectorException extends RuntimeException {

    public NonFiniteVectorException(double x, double y, double z) {
        super("vector components must be finite, was (%s, %s, %s)".formatted(x, y, z));
    }
}
