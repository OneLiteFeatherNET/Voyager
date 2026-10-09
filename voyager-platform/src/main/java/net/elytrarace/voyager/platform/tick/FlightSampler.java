package net.elytrarace.voyager.platform.tick;

import java.util.List;

/**
 * Where {@link FlightTickDriver} gets its per-tick observations.
 *
 * <p>The driver owns the call rather than taking samples as a parameter, and that is the whole
 * reason this interface exists. The order <em>rotation is written, a world tick passes, the state is
 * sampled</em> is load-bearing and invisible when broken; a caller that hands the driver a batch it
 * gathered at some earlier moment reintroduces the offset with nothing to catch it. Here the driver
 * calls at the one moment that is correct: the top of its own tick, before anything it does has
 * moved the simulation forward.
 *
 * <p>Implementations read live players on a server and return one {@link FlightSample} per player
 * being tracked. Returning a player who is not flying is not an error — that is how a flight is seen
 * to end.
 */
@FunctionalInterface
public interface FlightSampler {

    /**
     * Observes every tracked player at the current tick boundary.
     *
     * @return one sample per tracked player, in a stable order; empty when nobody is tracked
     */
    List<FlightSample> sampleAtTickBoundary();
}
