package net.elytrarace.voyager.race.flow;

import net.elytrarace.voyager.race.flow.exception.IllegalPhaseTransitionException;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The compact constructor's two guards, neither of which was exercised until the final review
 * deleted both and watched the suite stay green.
 *
 * <p>They deliberately throw different exceptions, and the split is the stage's rule rather than an
 * accident: a {@code mapIndex} can be read back in from persisted state, so a caller may want to
 * catch a bad one and gets {@link IllegalPhaseTransitionException} — the same exception
 * {@link RaceStateMachine#advance} throws for an index past the end of the cup. {@code inPhase} is
 * only ever computed by the state machine, so a negative one is a bug and gets
 * {@link IllegalArgumentException}.
 */
class RaceStateTest {

    @Test
    void initialStartsInTheLobbyOnTheFirstMapWithAZeroedClock() {
        RaceState initial = RaceState.initial();

        assertThat(initial.phase()).isEqualTo(RacePhase.LOBBY);
        assertThat(initial.mapIndex()).isZero();
        assertThat(initial.inPhase()).isEqualTo(Duration.ZERO);
        assertThat(initial.cupFinished()).isFalse();
    }

    @Test
    void rejectsANegativeMapIndexWithTheSameExceptionAdvanceThrowsForOneOutOfRange() {
        assertThatThrownBy(() -> new RaceState(RacePhase.LOBBY, -1, Duration.ZERO, false))
                .isInstanceOf(IllegalPhaseTransitionException.class)
                .hasMessageContaining("map index");
    }

    @Test
    void rejectsANegativeTimeInPhase() {
        assertThatThrownBy(() -> new RaceState(RacePhase.GAME, 0, Duration.ofSeconds(-1), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("time in phase");
    }
}
