package net.elytrarace.voyager.physics.exception;

/** Thrown when the simulation is handed a state it cannot advance. */
public final class InvalidSimulationStateException extends RuntimeException {

    public InvalidSimulationStateException(String message) {
        super(message);
    }
}
