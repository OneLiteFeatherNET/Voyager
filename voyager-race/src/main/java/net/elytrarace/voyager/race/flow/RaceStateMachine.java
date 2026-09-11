package net.elytrarace.voyager.race.flow;

import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.race.flow.exception.IllegalPhaseTransitionException;

import org.jetbrains.annotations.ApiStatus;

import java.time.Duration;

/**
 * Owns which transitions exist between {@link RacePhase}s and across a {@link CupDefinition}'s map
 * rotation. Ticking, timers and delays belong to whatever drives this in a loop — {@code
 * voyager-platform}'s Xerus integration later, a plain loop in this module's own tests today — not to
 * this class. That split is what makes a whole cup runnable inside a single JUnit run: see
 * {@code RaceStateMachineTest}'s cup-rotation test for the transition the old tree's linear phase
 * series could never make — it loads map two and then advances past {@code END} with no way back
 * into {@code GAME}.
 */
@ApiStatus.Internal
public abstract class RaceStateMachine {

    private RaceStateMachine() {
    }

    /**
     * Returns the state after {@code step} more time in {@code state}'s current phase.
     *
     * <p>A {@code state} with {@link RaceState#cupFinished()} true is returned unchanged: that is the
     * cup's normal terminal state, not an error. Otherwise {@code state.mapIndex()} must be a valid
     * index into {@code cup.mapNames()}, or this throws {@link IllegalPhaseTransitionException} — an
     * index outside the cup's rotation is not a position a race can be in.
     *
     * <p><strong>The boundary overshoot is carried forward.</strong> When a step takes the clock past
     * a phase's duration, the new phase does not start at zero: it starts at
     * {@code inPhase - duration}, the part of the step that was spent after the boundary. So no time
     * is lost when {@code step} does not divide the phase durations, and a driver can sum
     * {@link RaceState#inPhase()} across phases and get the wall clock back. Two deliberate
     * exceptions, both because no boundary was crossed: a {@code GAME} phase ended early by
     * {@code allPlayersFinished}, and the terminal state of a finished cup, which has no next phase
     * to carry anything into.
     */
    public static RaceState advance(RaceState state, CupDefinition cup, RaceTimings timings, Duration step,
            boolean allPlayersFinished) {
        if (state.cupFinished()) {
            return state;
        }
        int mapCount = cup.mapNames().size();
        if (state.mapIndex() >= mapCount) {
            throw IllegalPhaseTransitionException.mapIndexOutOfRange(state.mapIndex(), mapCount);
        }

        Duration inPhase = state.inPhase().plus(step);
        return switch (state.phase()) {
            case LOBBY -> advanceLobby(state, timings, inPhase);
            case GAME -> advanceGame(state, timings, inPhase, allPlayersFinished);
            case END -> advanceEnd(state, cup, timings, inPhase, mapCount);
        };
    }

    private static RaceState advanceLobby(RaceState state, RaceTimings timings, Duration inPhase) {
        if (inPhase.compareTo(timings.lobby()) >= 0) {
            return new RaceState(RacePhase.GAME, state.mapIndex(), inPhase.minus(timings.lobby()), false);
        }
        return new RaceState(RacePhase.LOBBY, state.mapIndex(), inPhase, false);
    }

    private static RaceState advanceGame(RaceState state, RaceTimings timings, Duration inPhase,
            boolean allPlayersFinished) {
        // Ending early is not a boundary being crossed: no time was spent past a limit, so there is
        // no overshoot to carry and END starts at zero. Ending on the limit is, and it carries.
        if (allPlayersFinished) {
            return new RaceState(RacePhase.END, state.mapIndex(), Duration.ZERO, false);
        }
        if (inPhase.compareTo(timings.race()) >= 0) {
            return new RaceState(RacePhase.END, state.mapIndex(), inPhase.minus(timings.race()), false);
        }
        return new RaceState(RacePhase.GAME, state.mapIndex(), inPhase, false);
    }

    private static RaceState advanceEnd(RaceState state, CupDefinition cup, RaceTimings timings, Duration inPhase,
            int mapCount) {
        if (inPhase.compareTo(timings.end()) < 0) {
            return new RaceState(RacePhase.END, state.mapIndex(), inPhase, false);
        }
        Duration overshoot = inPhase.minus(timings.end());
        if (cup.mode() == GameMode.PRACTICE) {
            return new RaceState(RacePhase.LOBBY, state.mapIndex(), overshoot, false);
        }
        int nextMapIndex = state.mapIndex() + 1;
        if (nextMapIndex < mapCount) {
            return new RaceState(RacePhase.LOBBY, nextMapIndex, overshoot, false);
        }
        // The one place the overshoot is deliberately dropped: no phase follows a finished cup, so
        // there is nothing for it to be carried into. The terminal state's clock reads zero because
        // it measures a phase that is over, not one that is running.
        return new RaceState(RacePhase.END, state.mapIndex(), Duration.ZERO, true);
    }
}
