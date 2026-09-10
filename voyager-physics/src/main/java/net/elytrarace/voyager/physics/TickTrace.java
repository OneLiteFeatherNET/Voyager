package net.elytrarace.voyager.physics;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.physics.FlightState;
import net.elytrarace.voyager.physics.step.ElytraStep;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * The outcome of one traced tick: the resulting {@link FlightState}, plus the velocity observed
 * after each {@link ElytraStep} ran.
 *
 * <p>{@code velocityAfter} is defensively copied into an {@link EnumMap}, which always iterates in
 * enum-declaration order regardless of insertion order — {@link ElytraStep}'s five constants are
 * declared in Vanilla's order, so iterating {@code velocityAfter.keySet()} yields that same order.
 * A caller diagnosing a divergence can therefore name the step that produced it, not only the tick.
 *
 * @param result the state produced by the tick
 * @param velocityAfter the velocity recorded immediately after each step ran, keyed by step, in
 *     Vanilla's order
 */
public record TickTrace(FlightState result, Map<ElytraStep, Vec3> velocityAfter) {

    public TickTrace {
        velocityAfter = Collections.unmodifiableMap(new EnumMap<>(velocityAfter));
    }
}
