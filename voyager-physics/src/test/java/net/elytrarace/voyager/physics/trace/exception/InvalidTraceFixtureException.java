package net.elytrarace.voyager.physics.trace.exception;

/**
 * Thrown when a {@code TraceFixture} cannot be constructed or read: a fixture with no ticks, a
 * tick whose index breaks the consecutive-from-zero contract, a non-finite sample, or JSON that
 * {@code TraceFixtureLoader} cannot parse into the fixture format at all.
 *
 * <p>This is the harness's own domain exception, not E2a's {@code InvalidTraceException} —
 * {@code voyager-physics} does not depend on {@code tools:trace-recorder}, so the fixture format is
 * mirrored here on the reading side rather than shared as a type.
 */
public final class InvalidTraceFixtureException extends RuntimeException {

    public InvalidTraceFixtureException(String message) {
        super(message);
    }
}
