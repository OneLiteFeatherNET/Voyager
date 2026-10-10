package net.elytrarace.voyager.platform.tick;

import net.elytrarace.voyager.api.physics.FlightInput;
import net.elytrarace.voyager.api.physics.FlightState;
import net.elytrarace.voyager.platform.flight.FlightTransition;

import java.util.UUID;

/**
 * What one player's tick did, with the pairing spelled out: {@code input} was applied to
 * {@code before} and produced {@code after}.
 *
 * <p>The three are carried together on purpose. The E2a recorder's second defect was a state and a
 * rotation that were each individually correct and paired one tick apart — the rotation stored at
 * index {@code k} had driven the transition <em>into</em> {@code k} rather than out of it — and
 * nothing downstream could see it, because every value was still self-consistent. A consumer of this
 * record can check the pairing by re-running the simulator, and {@code FlightTickDriverTest} does
 * exactly that on every tick.
 *
 * <p>For {@link FlightTransition.Ended} the flight was already over at this tick boundary: nothing
 * was simulated, {@code input} was <em>not</em> applied, and {@code before} and {@code after} are
 * both the final tracked state. For {@link FlightTransition.Started} and
 * {@link FlightTransition.Continuing} a step was simulated, and {@code after} always differs from
 * {@code before} by exactly that step.
 *
 * @param playerId whose tick this is
 * @param transition what {@code FlightTracker} made of the flag this tick
 * @param input the input sampled at the start of this tick
 * @param before the simulated state the tick started from
 * @param after the simulated state the tick ended at
 */
public record FlightTick(
        UUID playerId,
        FlightTransition transition,
        FlightInput input,
        FlightState before,
        FlightState after) {
}
