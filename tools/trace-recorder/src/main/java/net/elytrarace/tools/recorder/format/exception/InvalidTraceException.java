package net.elytrarace.tools.recorder.format.exception;

/** Thrown when a recorded trace violates an invariant that makes it unusable as a fixture. */
public final class InvalidTraceException extends RuntimeException {

    public InvalidTraceException(String message) {
        super(message);
    }
}
