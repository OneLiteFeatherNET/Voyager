package net.elytrarace.api.physics.exception;

/** Thrown when a yaw or pitch value is NaN or infinite. */
public final class NonFiniteRotationException extends RuntimeException {

    public NonFiniteRotationException(float yaw, float pitch) {
        super("rotation must be finite, was yaw=%s pitch=%s".formatted(yaw, pitch));
    }
}
