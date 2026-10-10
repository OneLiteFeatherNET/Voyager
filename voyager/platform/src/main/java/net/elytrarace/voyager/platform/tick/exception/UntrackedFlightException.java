package net.elytrarace.voyager.platform.tick.exception;

import java.util.UUID;

/**
 * Thrown when the flight tracker reports a flight that is already in progress for a player the tick
 * driver holds no simulated state for.
 *
 * <p>The two are meant to be forgotten together and the driver's {@code forget} does exactly that,
 * so this can only mean something advanced the tracker behind the driver's back. Silently re-seeding
 * from the observation would be the tempting repair and the wrong one: it would replace a simulated
 * trajectory with a client-reported position mid-flight, which is the one thing the server-side
 * simulation exists to avoid, and it would do it without a trace.
 */
public final class UntrackedFlightException extends RuntimeException {

    public UntrackedFlightException(UUID playerId) {
        super(("%s is reported as already flying but has no simulated state; the flight tracker and "
                + "the tick driver have fallen out of step").formatted(playerId));
    }
}
