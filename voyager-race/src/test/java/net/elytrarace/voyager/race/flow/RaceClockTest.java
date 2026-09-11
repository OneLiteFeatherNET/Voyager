package net.elytrarace.voyager.race.flow;

import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.GameMode;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the answer to the question E3 left open: <strong>what is the race clock?</strong>
 *
 * <p>{@link RaceState#inPhase()} is not it. It is the clock a tick <em>starts from</em>, so the
 * {@code N}-th movement tick of a {@code GAME} phase is played against {@code (N-1) * step} and a
 * finish timed from it reads one step short. {@link RaceClock} is it: the count of movement ticks a
 * driver has actually played, incremented before the tick it names is played.
 *
 * <p>The decisive assertion is {@link #theRaceClockRunsExactlyOneStepAheadOfThePhaseClock()}, which
 * does not merely check that the two agree somewhere — it asserts the gap, in the direction it
 * exists, on every single movement tick of a whole {@code GAME} phase. A driver that quietly
 * switched to {@code inPhase} as its race clock turns it red; so does one that stopped counting a
 * tick before playing it.
 */
class RaceClockTest {

    /** One Minecraft tick at 20 TPS. */
    private static final Duration STEP = Duration.ofMillis(50);

    /** Lobby 1 s, race 4 s, end 1 s — 20, 80 and 20 ticks. All three differ, as in E3's fixtures. */
    private static final RaceTimings TIMINGS =
            new RaceTimings(Duration.ofSeconds(1), Duration.ofSeconds(4), Duration.ofSeconds(1));

    private static final int GAME_TICKS = 80;

    private static final CupDefinition CUP =
            new CupDefinition("clock-cup", List.of("only-map"), GameMode.RACE);

    @Test
    void aFreshClockHasPlayedNothing() {
        RaceClock clock = RaceClock.startingAt(STEP);

        assertThat(clock.gameTick()).isZero();
        assertThat(clock.elapsed()).isEqualTo(Duration.ZERO);
    }

    @Test
    void advancingCountsOneMovementTick() {
        RaceClock clock = RaceClock.startingAt(STEP).advanced().advanced().advanced();

        assertThat(clock.gameTick()).isEqualTo(3);
        assertThat(clock.elapsed()).isEqualTo(Duration.ofMillis(150));
    }

    @Test
    void aClockWithoutAStepWouldMeasureNothing() {
        assertThatThrownBy(() -> new RaceClock(0, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("step");
        assertThatThrownBy(() -> new RaceClock(0, Duration.ofMillis(-50)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("step");
        assertThatThrownBy(() -> new RaceClock(-1, STEP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gameTick");
    }

    /**
     * The one-tick gap, asserted in the direction it exists, on every movement tick of a whole
     * {@code GAME} phase: {@code clock.elapsed() == state.inPhase() + step}.
     */
    @Test
    void theRaceClockRunsExactlyOneStepAheadOfThePhaseClock() {
        List<Sample> samples = playOneGamePhase();

        assertThat(samples).isNotEmpty();
        for (Sample sample : samples) {
            assertThat(sample.elapsed())
                    .as("movement tick %s: the race clock is the phase clock plus one step"
                            .formatted(sample.gameTick()))
                    .isEqualTo(sample.inPhase().plus(STEP));
            assertThat(sample.elapsed())
                    .as("movement tick %s: inPhase is not the race clock".formatted(sample.gameTick()))
                    .isNotEqualTo(sample.inPhase());
        }
    }

    /**
     * The consequence the brief names: a finish timed from {@code inPhase} is recorded one tick
     * early, and the last movement tick of the phase is where it shows worst — the phase clock
     * never reaches the phase's own duration, the race clock lands on it exactly.
     */
    @Test
    void theLastMovementTickOfThePhaseReadsTheFullRaceDurationOnTheRaceClockAndOneStepLessOnTheOther() {
        List<Sample> samples = playOneGamePhase();
        Sample last = samples.getLast();

        assertThat(samples).hasSize(GAME_TICKS);
        assertThat(last.gameTick()).isEqualTo(GAME_TICKS);
        assertThat(last.elapsed()).isEqualTo(TIMINGS.race());
        assertThat(last.inPhase()).isEqualTo(TIMINGS.race().minus(STEP));
        assertThat(last.inPhase()).isNotEqualTo(TIMINGS.race());
    }

    /** The first movement tick is played, and it is counted as tick one, not tick zero. */
    @Test
    void theFirstMovementTickOfThePhaseIsCountedBeforeItIsPlayed() {
        Sample first = playOneGamePhase().getFirst();

        assertThat(first.gameTick()).isEqualTo(1);
        assertThat(first.elapsed()).isEqualTo(STEP);
        assertThat(first.inPhase()).isEqualTo(Duration.ZERO);
    }

    /**
     * Plays a whole {@code GAME} phase with the canonical driver loop — advance the phase, then
     * count and play the movement tick against the state the advance produced — and records both
     * clocks on every movement tick.
     */
    private static List<Sample> playOneGamePhase() {
        RaceState state = RaceState.initial();
        RaceClock clock = RaceClock.startingAt(STEP);
        List<Sample> samples = new ArrayList<>();

        for (int tick = 0; tick < 1_000 && !state.cupFinished(); tick++) {
            RaceState next = RaceStateMachine.advance(state, CUP, TIMINGS, STEP, false);
            if (next.phase() == RacePhase.GAME && state.phase() != RacePhase.GAME) {
                clock = RaceClock.startingAt(STEP);
            }
            if (next.phase() == RacePhase.GAME) {
                clock = clock.advanced();
                samples.add(new Sample(clock.gameTick(), clock.elapsed(), next.inPhase()));
            }
            state = next;
        }
        return List.copyOf(samples);
    }

    private record Sample(int gameTick, Duration elapsed, Duration inPhase) {
    }
}
