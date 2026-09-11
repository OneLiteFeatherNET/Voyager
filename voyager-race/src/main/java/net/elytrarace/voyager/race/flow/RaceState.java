package net.elytrarace.voyager.race.flow;

import net.elytrarace.voyager.race.flow.exception.IllegalPhaseTransitionException;

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
        // The same exception RaceStateMachine.advance throws for an index past the end of the cup:
        // one value, one exception family. A mapIndex can arrive from persisted state rather than
        // only from live code, which is the reason it gets a domain exception while inPhase below —
        // computed here, never read in from anywhere — keeps IllegalArgumentException.
        if (mapIndex < 0) {
            throw IllegalPhaseTransitionException.negativeMapIndex(mapIndex);
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
