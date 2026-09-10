package net.elytrarace.voyager.api.physics;

import net.elytrarace.voyager.api.physics.exception.NonFiniteRotationException;

import net.elytrarace.voyager.api.math.Vec3;

/**
 * The complete state of one gliding entity at a tick boundary.
 *
 * <p>Rotation is {@code float} and position and velocity are {@code double}, mirroring Vanilla's own
 * numeric types. Widening rotation to {@code double} looks harmless and produces reproducible drift
 * against the recorded traces.
 */
public record FlightState(Vec3 position, Vec3 velocity, float yaw, float pitch, boolean onGround) {

    public FlightState {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new NonFiniteRotationException(yaw, pitch);
        }
    }
}
