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
 *
 * <p><b>{@code fireworkBoostActive} means "an impulse ran during this tick", which is not what a
 * recording's own flag means.</b> E2a's recorder samples its rocket list one tick after the tick it
 * describes, so a rocket that boosted and then detonated is already gone from the sample and the
 * recorded flag reads {@code false} for a boosted tick. A caller feeding a fixture in must translate;
 * {@code voyager-physics}'s {@code RecordedGlide} is where that translation lives and where the
 * reasoning is written down. A live server caller has the rocket in hand and needs no translation.
 *
 * <p><b>Known limit: one impulse per tick.</b> Vanilla attaches more than one rocket at a time and
 * applies each rocket's impulse separately, and because the impulse blends toward
 * {@code 1.7 · lookAngle} rather than adding, two rockets converge nearer that terminal speed instead
 * of doubling it — measurably so: E2a's {@code chained-boosts} recording settles at
 * {@code velZ = 1.686154} where {@code single-boost} settles at {@code 1.671612}. A {@code boolean}
 * cannot express that, so a game that lets a pilot chain rockets will need this field to become a
 * count. It is left a {@code boolean} here deliberately: the shape of the field is an API decision
 * rather than a fix to make a test pass, and no fixture can currently validate the count either
 * (see {@code docs/reference/elytra-physics-26.2.md}, "Measured parity").
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
