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
 * <p>The map has no entry for the firework boost: it is applied before the first {@link
 * ElytraStep} runs, so {@code velocityAfter.get(ElytraStep.GRAVITY_AND_LIFT)} is already the
 * post-boost velocity with that step's own contribution folded in. A divergence traced to a
 * boosted tick cannot currently be attributed to the boost or to {@code GRAVITY_AND_LIFT}
 * individually from this map alone — only to the tick as a whole via {@code GRAVITY_AND_LIFT}'s
 * entry. Giving the boost its own entry would need a key type broader than {@link ElytraStep} (it
 * is not one of Vanilla's five {@code updateFallFlyingMovement} branches), which would touch the
 * shape this record was reviewed and pinned against in Task 5 — left as a follow-up decision rather
 * than changed unilaterally here.
 *
 * @param result the state produced by the tick
 * @param velocityAfter the velocity recorded immediately after each step ran, keyed by step, in
 *     Vanilla's order
 */
public record TickTrace(FlightState result, Map<ElytraStep, Vec3> velocityAfter) {

    public TickTrace {
        EnumMap<ElytraStep, Vec3> copy = new EnumMap<>(ElytraStep.class);
        copy.putAll(velocityAfter);
        velocityAfter = Collections.unmodifiableMap(copy);
    }
}
