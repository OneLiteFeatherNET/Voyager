package net.elytrarace.voyager.physics.trace.exception;

/**
 * Thrown when {@code TraceReplay.replay} is called with a per-tick or cumulative threshold that
 * cannot be compared against a position error at all — non-finite or negative.
 *
 * <p>Deliberately not {@link InvalidTraceFixtureException}: a bad threshold is a defect in the
 * call, not in the fixture being replayed, and a caller catching one should not have to wonder
 * whether the fixture itself was at fault.
 */
public final class InvalidReplayThresholdException extends RuntimeException {

    public InvalidReplayThresholdException(String message) {
        super(message);
    }
}
