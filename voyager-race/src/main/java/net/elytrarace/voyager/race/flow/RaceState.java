package net.elytrarace.voyager.race.flow;

import java.time.Duration;

/**
 * A race's position in the state machine: current {@link RacePhase}, which map in the cup's
 * rotation is active, how long the current phase has run, and whether the cup as a whole is done.
 *
 * <p>{@code cupFinished} is terminal, not an error state: once true, {@link RaceStateMachine#advance}
 * returns the state unchanged rather than throwing, because running out of maps in a {@code RACE}
 * cup is the normal end of a race.
 */
public record RaceState(RacePhase phase, int mapIndex, Duration inPhase, boolean cupFinished) {

    public RaceState {
        if (mapIndex < 0) {
            throw new IllegalArgumentException("map index must not be negative, was %d".formatted(mapIndex));
        }
        if (inPhase.isNegative()) {
            throw new IllegalArgumentException("time in phase must not be negative, was %s".formatted(inPhase));
        }
    }

    /** The state a fresh cup starts in: lobby, first map, no time elapsed, not finished. */
    public static RaceState initial() {
        return new RaceState(RacePhase.LOBBY, 0, Duration.ZERO, false);
    }
}
