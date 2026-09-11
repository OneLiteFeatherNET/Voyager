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
 *
 * <h2>Which side of the tick {@code inPhase} is on</h2>
 *
 * <p>{@code inPhase} is the time that had elapsed in this phase <em>before</em> the tick this state
 * describes — it is the clock the tick starts from, not the one it ends on. A driver that ticks
 * movement on every state whose phase is {@code GAME} therefore sees:
 *
 * <pre>
 *   entering GAME        inPhase = 0 (or the lobby's overshoot), and the transition tick's
 *                        movement is played against it
 *   the phase's Nth      inPhase = (N-1) * step
 *   movement tick
 *   an 8 s GAME phase    the last movement tick reads 7.950 s at a 50 ms step, never 8.000 s
 * </pre>
 *
 * <p>So {@code inPhase} is <strong>not</strong> a race clock: a finish time taken from it reads one
 * step short. A finish is timed by counting the movement ticks the driver actually played — see
 * {@code CupPlaythroughTest}, which scores a finisher on {@code TICK.multipliedBy(gameTick)} and
 * asserts the result is not the phase duration. Giving a race run a type that owns both clocks is
 * E4's job (finding F2); until then this paragraph is the contract.
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
