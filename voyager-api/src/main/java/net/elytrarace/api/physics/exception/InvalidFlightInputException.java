package net.elytrarace.api.physics.exception;

/** Thrown when flight input violates an invariant that no client can legitimately produce. */
public final class InvalidFlightInputException extends RuntimeException {

    public InvalidFlightInputException(String message) {
        super(message);
    }
}
