package net.elytrarace.voyager.race.flow.exception;

/**
 * Thrown when {@link net.elytrarace.voyager.race.flow.RaceStateMachine#advance} is asked to advance a
 * {@code RaceState} whose {@code mapIndex} does not exist in the cup it is being advanced against.
 * This is a programming error — a {@code mapIndex} can drift out of range only through a caller
 * building or persisting a state incorrectly — not a game outcome.
 */
public final class IllegalPhaseTransitionException extends RuntimeException {

    private IllegalPhaseTransitionException(String message) {
        super(message);
    }

    public static IllegalPhaseTransitionException mapIndexOutOfRange(int mapIndex, int mapCount) {
        return new IllegalPhaseTransitionException(
                "map index %d is out of range for a cup with %d map(s)".formatted(mapIndex, mapCount));
    }
}
