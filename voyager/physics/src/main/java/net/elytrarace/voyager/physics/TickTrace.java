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
 * <p>The map covers the five {@code updateFallFlyingMovement} branches and nothing else. Two parts
 * of the tick are outside it, on either side:
 *
 * <ul>
 *   <li>{@code LivingEntity.aiStep()}'s {@code 0.003} deadzone runs <em>before</em> the first step,
 *       so {@code velocityAfter.get(ElytraStep.GRAVITY_AND_LIFT)} is already post-deadzone with
 *       that step's own contribution folded in. A divergence caused by a clamped component shows up
 *       there and cannot be separated from {@code GRAVITY_AND_LIFT}'s own arithmetic through this
 *       map alone.</li>
 *   <li>The firework impulse runs <em>after</em> the move and after collision restitution, so it
 *       appears in no entry at all: {@code velocityAfter.get(ElytraStep.DRAG)} is the pre-collision,
 *       pre-boost velocity, while {@code result().velocity()} carries both.</li>
 * </ul>
 *
 * <p>Giving either its own entry would need a key type broader than {@link ElytraStep} — neither is
 * one of Vanilla's five branches — which would touch the shape this record was reviewed and pinned
 * against in Task 5; left as a follow-up decision rather than changed unilaterally here.
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
