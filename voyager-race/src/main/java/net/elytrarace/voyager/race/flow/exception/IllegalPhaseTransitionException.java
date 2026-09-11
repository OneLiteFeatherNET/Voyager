package net.elytrarace.voyager.race.flow.exception;

/**
 * Thrown when a {@code RaceState}'s {@code mapIndex} is not a position a cup could ever be at:
 * negative when the state is built, or past the end of the cup's rotation when
 * {@link net.elytrarace.voyager.race.flow.RaceStateMachine#advance} is asked to advance it.
 *
 * <p>Both guards throw this one exception on purpose. A {@code mapIndex} is the one value in the
 * flow package that does not have to come from live code — a persisted or replayed race carries it
 * back in from outside — so a caller may reasonably want to catch a bad one and fall back to a
 * fresh cup. That is the criterion this stage uses to separate a domain exception from an
 * {@code IllegalArgumentException}: the internally computed values beside it ({@code inPhase}, the
 * phase durations) keep {@code IllegalArgumentException}, because nothing but a bug can produce
 * them wrong.
 */
public final class IllegalPhaseTransitionException extends RuntimeException {

    private IllegalPhaseTransitionException(String message) {
        super(message);
    }

    public static IllegalPhaseTransitionException negativeMapIndex(int mapIndex) {
        return new IllegalPhaseTransitionException("map index must not be negative, was %d".formatted(mapIndex));
    }

    public static IllegalPhaseTransitionException mapIndexOutOfRange(int mapIndex, int mapCount) {
        return new IllegalPhaseTransitionException(
                "map index %d is out of range for a cup with %d map(s)".formatted(mapIndex, mapCount));
    }
}
