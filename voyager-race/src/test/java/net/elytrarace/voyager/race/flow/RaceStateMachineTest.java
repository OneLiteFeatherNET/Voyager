package net.elytrarace.voyager.race.flow;

import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.race.flow.exception.IllegalPhaseTransitionException;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Three maps with distinct names, and lobby/race/end durations that are all different from each
 * other. Equal durations would let a machine that reads the wrong {@link RaceTimings} field pass
 * every test here; two maps (or maps sharing a name) would not distinguish "moved to the next map"
 * from "stayed on the same one."
 */
class RaceStateMachineTest {

    private static final List<String> MAP_NAMES = List.of("nether-sprint", "sky-loop", "canyon-run");
    private static final CupDefinition RACE_CUP = new CupDefinition("winter-cup", MAP_NAMES, GameMode.RACE);
    private static final CupDefinition PRACTICE_CUP = new CupDefinition("warmup", MAP_NAMES, GameMode.PRACTICE);

    private static final RaceTimings TIMINGS =
            new RaceTimings(Duration.ofSeconds(5), Duration.ofSeconds(20), Duration.ofSeconds(3));
    private static final Duration STEP = Duration.ofSeconds(1);

    @Test
    void defaultTimingsMatchTheOldTree() {
        assertThat(RaceTimings.DEFAULT.lobby()).isEqualTo(Duration.ofSeconds(120));
        assertThat(RaceTimings.DEFAULT.race()).isEqualTo(Duration.ofSeconds(300));
        assertThat(RaceTimings.DEFAULT.end()).isEqualTo(Duration.ofSeconds(100));
    }

    @Test
    void lobbyHoldsUntilItsDurationElapsesThenMovesToGameWithResetTime() {
        RaceState state = RaceState.initial();

        // One tick short of the 5 s lobby boundary: still LOBBY, time keeps accumulating.
        for (int second = 1; second <= 4; second++) {
            state = RaceStateMachine.advance(state, RACE_CUP, TIMINGS, STEP, false);
            assertThat(state.phase()).isEqualTo(RacePhase.LOBBY);
            assertThat(state.inPhase()).isEqualTo(Duration.ofSeconds(second));
            assertThat(state.mapIndex()).isZero();
        }

        // The tick that reaches the 5 s boundary exactly: moves on, and the clock resets.
        state = RaceStateMachine.advance(state, RACE_CUP, TIMINGS, STEP, false);

        assertThat(state.phase()).isEqualTo(RacePhase.GAME);
        assertThat(state.inPhase()).isEqualTo(Duration.ZERO);
        assertThat(state.mapIndex()).isZero();
    }

    @Test
    void raceEndsOnTheTimeLimit() {
        RaceState state = new RaceState(RacePhase.GAME, 0, Duration.ZERO, false);

        // One tick short of the 20 s race boundary: still GAME.
        for (int second = 1; second <= 19; second++) {
            state = RaceStateMachine.advance(state, RACE_CUP, TIMINGS, STEP, false);
        }
        assertThat(state.phase()).isEqualTo(RacePhase.GAME);
        assertThat(state.inPhase()).isEqualTo(Duration.ofSeconds(19));

        state = RaceStateMachine.advance(state, RACE_CUP, TIMINGS, STEP, false);

        assertThat(state.phase()).isEqualTo(RacePhase.END);
        assertThat(state.inPhase()).isEqualTo(Duration.ZERO);
    }

    @Test
    void raceEndsEarlyWhenEveryPlayerFinishes() {
        RaceState state = new RaceState(RacePhase.GAME, 0, Duration.ofSeconds(6), false);

        state = RaceStateMachine.advance(state, RACE_CUP, TIMINGS, STEP, true);

        assertThat(state.phase()).isEqualTo(RacePhase.END);
        assertThat(state.inPhase()).isEqualTo(Duration.ZERO);
        // 7 s elapsed in GAME, well short of the 20 s limit — it ended because of the flag, not time.
    }

    @Test
    void endReturnsToLobbyOnTheNextMapWhenARaceCupHasMoreMaps() {
        // One tick short of the 3 s end boundary: still END, same map, time keeps accumulating.
        RaceState oneTickShort = new RaceState(RacePhase.END, 0, Duration.ofSeconds(1), false);
        oneTickShort = RaceStateMachine.advance(oneTickShort, RACE_CUP, TIMINGS, STEP, false);
        assertThat(oneTickShort.phase()).isEqualTo(RacePhase.END);
        assertThat(oneTickShort.inPhase()).isEqualTo(Duration.ofSeconds(2));
        assertThat(oneTickShort.mapIndex()).isZero();

        // This is the transition the old tree cannot make: GamePhaseFactory builds a linear,
        // three-phase series (lobby -> game -> end) with no edge back into game. Its finish()
        // callback loads map two's data and announces it, then the linear series advances past
        // `end` anyway, so map two is loaded but never actually run. Here, the tick that reaches
        // the 3 s boundary while ending map 0 of a RACE cup with maps remaining goes back to
        // LOBBY on map 1.
        RaceState rotated = RaceStateMachine.advance(oneTickShort, RACE_CUP, TIMINGS, STEP, false);

        assertThat(rotated.phase()).isEqualTo(RacePhase.LOBBY);
        assertThat(rotated.mapIndex()).isEqualTo(1);
        assertThat(rotated.inPhase()).isEqualTo(Duration.ZERO);
        assertThat(rotated.cupFinished()).isFalse();
    }

    @Test
    void endRotatesThroughEveryMapBeforeFinishingTheCup() {
        // Drive a full RACE cup end to end: map 0 -> map 1 -> map 2 -> cup finished. Chained rather
        // than only checked at the first map, so a mutation that finishes the cup one map early or
        // late (Mutation 6) is caught regardless of which map it happens to trip on.
        RaceState state = new RaceState(RacePhase.END, 0, Duration.ofSeconds(2), false);
        state = RaceStateMachine.advance(state, RACE_CUP, TIMINGS, STEP, false);
        assertThat(state.phase()).isEqualTo(RacePhase.LOBBY);
        assertThat(state.mapIndex()).isEqualTo(1);
        assertThat(state.cupFinished()).isFalse();

        state = new RaceState(RacePhase.END, state.mapIndex(), Duration.ofSeconds(2), false);
        state = RaceStateMachine.advance(state, RACE_CUP, TIMINGS, STEP, false);
        assertThat(state.phase()).isEqualTo(RacePhase.LOBBY);
        assertThat(state.mapIndex()).isEqualTo(2);
        assertThat(state.cupFinished()).isFalse();

        state = new RaceState(RacePhase.END, state.mapIndex(), Duration.ofSeconds(2), false);
        state = RaceStateMachine.advance(state, RACE_CUP, TIMINGS, STEP, false);

        assertThat(state.phase()).isEqualTo(RacePhase.END);
        assertThat(state.mapIndex()).isEqualTo(2);
        assertThat(state.cupFinished()).isTrue();
    }

    @Test
    void aFinishedCupIsTerminalAndAdvancingItChangesNothing() {
        RaceState finished = new RaceState(RacePhase.END, 2, Duration.ofSeconds(2), true);

        RaceState next = RaceStateMachine.advance(finished, RACE_CUP, TIMINGS, STEP, false);

        assertThat(next).isEqualTo(finished);
        // Advancing again, and with a flag that would otherwise change behaviour, still changes
        // nothing — the terminal state does not throw and does not move.
        assertThat(RaceStateMachine.advance(next, RACE_CUP, TIMINGS, STEP, true)).isEqualTo(finished);
    }

    @Test
    void practiceReturnsToLobbyOnTheSameMapRegardlessOfHowManyMapsTheCupHas() {
        RaceState state = new RaceState(RacePhase.END, 1, Duration.ofSeconds(2), false);

        state = RaceStateMachine.advance(state, PRACTICE_CUP, TIMINGS, STEP, false);

        assertThat(state.phase()).isEqualTo(RacePhase.LOBBY);
        assertThat(state.mapIndex()).isEqualTo(1);
        assertThat(state.cupFinished()).isFalse();

        // It loops on the same map indefinitely: run it again and the index still does not move,
        // even though this PRACTICE cup has three maps in its rotation.
        state = new RaceState(RacePhase.GAME, 1, Duration.ZERO, false);
        state = RaceStateMachine.advance(state, PRACTICE_CUP, TIMINGS, Duration.ofSeconds(20), false);
        state = RaceStateMachine.advance(state, PRACTICE_CUP, TIMINGS, Duration.ofSeconds(3), false);

        assertThat(state.phase()).isEqualTo(RacePhase.LOBBY);
        assertThat(state.mapIndex()).isEqualTo(1);
    }

    /**
     * A step that does not divide the phase durations. Every test above uses 1 s against 5/20/3 s,
     * and the gate uses 50 ms against 2/8/1 s, so until this was written nothing in the suite could
     * tell "the overshoot is carried" from "the overshoot is discarded" — the whole clock contract
     * was untested, not just its edges.
     *
     * <p>With a 3 s step: the lobby's 5 s boundary is crossed at 6 s, so {@code GAME} starts at 1 s,
     * not at zero. From there the 20 s race boundary is crossed at 22 s, so {@code END} starts at
     * 2 s. From there the 3 s end boundary is crossed at 5 s, so the next map's lobby starts at 2 s.
     * Discarding the overshoot instead makes every phase run up to one step long and loses 5 s of
     * wall clock over one map.
     */
    @Test
    void aStepThatDoesNotDivideThePhaseDurationsCarriesTheOvershootIntoTheNextPhase() {
        Duration threeSecondStep = Duration.ofSeconds(3);
        RaceState state = RaceState.initial();

        state = RaceStateMachine.advance(state, RACE_CUP, TIMINGS, threeSecondStep, false);
        assertThat(state.phase()).isEqualTo(RacePhase.LOBBY);
        assertThat(state.inPhase()).isEqualTo(Duration.ofSeconds(3));

        state = RaceStateMachine.advance(state, RACE_CUP, TIMINGS, threeSecondStep, false);
        assertThat(state.phase()).as("6 s is past the 5 s lobby").isEqualTo(RacePhase.GAME);
        assertThat(state.inPhase()).as("the 1 s spent past the lobby boundary").isEqualTo(Duration.ofSeconds(1));

        // 1 s + seven 3 s steps = 22 s, the first value past the 20 s race boundary.
        for (int step = 1; step <= 6; step++) {
            state = RaceStateMachine.advance(state, RACE_CUP, TIMINGS, threeSecondStep, false);
            assertThat(state.phase()).isEqualTo(RacePhase.GAME);
        }
        assertThat(state.inPhase()).isEqualTo(Duration.ofSeconds(19));

        state = RaceStateMachine.advance(state, RACE_CUP, TIMINGS, threeSecondStep, false);
        assertThat(state.phase()).isEqualTo(RacePhase.END);
        assertThat(state.inPhase()).as("the 2 s spent past the race boundary").isEqualTo(Duration.ofSeconds(2));

        state = RaceStateMachine.advance(state, RACE_CUP, TIMINGS, threeSecondStep, false);
        assertThat(state.phase()).isEqualTo(RacePhase.LOBBY);
        assertThat(state.mapIndex()).isEqualTo(1);
        assertThat(state.inPhase()).as("the 2 s spent past the end boundary").isEqualTo(Duration.ofSeconds(2));
    }

    /**
     * The same carry on a {@code PRACTICE} cup, which returns to the lobby on its own map rather
     * than rotating — a separate branch, and one an overshoot could silently be dropped in.
     */
    @Test
    void practiceCarriesTheOvershootBackIntoItsOwnLobby() {
        RaceState state = new RaceState(RacePhase.END, 1, Duration.ofSeconds(2), false);

        state = RaceStateMachine.advance(state, PRACTICE_CUP, TIMINGS, Duration.ofSeconds(3), false);

        assertThat(state.phase()).isEqualTo(RacePhase.LOBBY);
        assertThat(state.mapIndex()).isEqualTo(1);
        assertThat(state.inPhase()).as("5 s against a 3 s end phase leaves 2 s").isEqualTo(Duration.ofSeconds(2));
    }

    /**
     * The tick-alignment F1 names, asserted rather than left implicit: {@code inPhase} is the clock
     * a tick <em>starts</em> from. The first {@code GAME} state a driver sees reads zero and is the
     * tick on which the first movement is played, so the phase's Nth movement tick reads
     * {@code (N-1) * step} and the last one is a whole step short of the phase duration.
     */
    @Test
    void inPhaseIsTheClockATickStartsFromSoTheLastGameTickIsOneStepShortOfTheLimit() {
        RaceState state = new RaceState(RacePhase.GAME, 0, Duration.ZERO, false);

        // Movement tick 1 is played against inPhase 0 — the state above. Tick 2 reads one step.
        state = RaceStateMachine.advance(state, RACE_CUP, TIMINGS, STEP, false);
        assertThat(state.inPhase()).isEqualTo(Duration.ofSeconds(1));

        for (int movementTick = 3; movementTick <= 20; movementTick++) {
            state = RaceStateMachine.advance(state, RACE_CUP, TIMINGS, STEP, false);
            assertThat(state.phase()).isEqualTo(RacePhase.GAME);
            assertThat(state.inPhase()).isEqualTo(Duration.ofSeconds(movementTick - 1L));
        }

        // Twenty movement ticks were played in a 20 s phase, and the last of them read 19 s. A
        // finish timed from inPhase would therefore be one step short of the truth.
        assertThat(state.inPhase()).isEqualTo(TIMINGS.race().minus(STEP));
    }

    @Test
    void aMapIndexBeyondTheCupsMapListThrows() {
        RaceState outOfRange = new RaceState(RacePhase.LOBBY, MAP_NAMES.size(), Duration.ZERO, false);

        assertThatThrownBy(() -> RaceStateMachine.advance(outOfRange, RACE_CUP, TIMINGS, STEP, false))
                .isInstanceOf(IllegalPhaseTransitionException.class);
    }
}
