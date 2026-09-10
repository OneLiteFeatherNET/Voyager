package net.elytrarace.voyager.api.physics;

import net.elytrarace.voyager.api.physics.exception.NonFiniteRotationException;
import net.elytrarace.voyager.api.physics.exception.InvalidFlightInputException;

/**
 * The per-tick input driving a simulated glide.
 *
 * <p>{@code gravity} is not a constant: Vanilla's lift term is
 * {@code gravity * (-1.0 + liftForce * 0.75)}, read each tick from {@code getEffectiveGravity()},
 * and Slow Falling clamps it to {@code 0.01} while descending. It is therefore supplied per tick,
 * alongside rotation.
 *
 * <p>{@code fireworkTicksRemaining} is carried but never read by the simulation. Vanilla's impulse
 * is applied by the rocket entity's own {@code tick()} for as long as the rocket is attached, and
 * whether it fires on a given tick is exactly {@code fireworkBoostActive}; the remaining lifetime
 * does not enter the formula. It is part of this record for format parity with the E2a trace
 * recorder, which records the counter per tick: a fixture round-trips through {@code FlightInput}
 * without losing a field, and a recording whose {@code fireworkBoostActive} flags disagree with its
 * countdown can be spotted rather than silently discarded. A later Vanilla version that made the
 * impulse depend on the rocket's age would find the value already threaded through.
 */
public record FlightInput(
        float yaw, float pitch, boolean fireworkBoostActive, int fireworkTicksRemaining, double gravity) {

    public FlightInput {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new NonFiniteRotationException(yaw, pitch);
        }
        if (fireworkTicksRemaining < 0) {
            throw new InvalidFlightInputException(
                    "fireworkTicksRemaining must be >= 0, was %s".formatted(fireworkTicksRemaining));
        }
        if (!Double.isFinite(gravity) || gravity <= 0.0) {
            throw new InvalidFlightInputException(
                    "gravity must be finite and > 0, was %s".formatted(gravity));
        }
    }
}
