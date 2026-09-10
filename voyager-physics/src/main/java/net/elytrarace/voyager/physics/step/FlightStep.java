package net.elytrarace.voyager.physics.step;

import net.elytrarace.voyager.api.math.Vec3;

/**
 * One named step of Vanilla's {@code updateFallFlyingMovement}, applied to the velocity entering
 * the tick.
 *
 * <p>Injectable so a harness can substitute or intercept an individual step without touching
 * {@link ElytraStep}'s ordering.
 */
@FunctionalInterface
public interface FlightStep {

    Vec3 apply(Vec3 velocity, StepContext context);
}
