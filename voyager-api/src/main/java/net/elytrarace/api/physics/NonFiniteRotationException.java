package net.elytrarace.api.physics;

/** Thrown when a yaw or pitch value is NaN or infinite. */
public final class NonFiniteRotationException extends RuntimeException {

    public NonFiniteRotationException(float yaw, float pitch) {
        super("rotation must be finite, was yaw=" + yaw + " pitch=" + pitch);
    }
}
