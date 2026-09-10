package net.elytrarace.api.physics;

import net.elytrarace.api.physics.exception.NonFiniteRotationException;
import net.elytrarace.api.physics.exception.InvalidFlightInputException;

/** The per-tick input driving a simulated glide. */
public record FlightInput(float yaw, float pitch, boolean fireworkBoostActive, int fireworkTicksRemaining) {

    public FlightInput {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new NonFiniteRotationException(yaw, pitch);
        }
        if (fireworkTicksRemaining < 0) {
            throw new InvalidFlightInputException(
                    "fireworkTicksRemaining must be >= 0, was %s".formatted(fireworkTicksRemaining));
        }
    }
}
