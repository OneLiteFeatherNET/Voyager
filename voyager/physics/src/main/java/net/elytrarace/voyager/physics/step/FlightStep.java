package net.elytrarace.voyager.physics.step;

import net.elytrarace.voyager.api.math.Vec3;

/**
 * One named step of Vanilla's {@code updateFallFlyingMovement}, applied to the velocity entering
 * the tick.
 *
 * <p>A function type, not an extension point. The five implementations are the constants of
 * {@link ElytraStep} and nothing outside that enum supplies one: {@link ElytraStep#stepsInOrder()}
 * is the only sequence any caller runs, and there is no seam for substituting or intercepting a
 * step. Naming the shape lets each constant carry its own body and its own javadoc instead of one
 * {@code switch} over the enum; it does not invite a configuration point, which the design for this
 * module rules out.
 */
@FunctionalInterface
public interface FlightStep {

    Vec3 apply(Vec3 velocity, StepContext context);
}
