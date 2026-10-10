package net.elytrarace.voyager.race.flow;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The negativity guard, which nothing exercised until the final review deleted it and watched the
 * suite stay green, and the {@code END} split that {@link RaceTimings#end(boolean)} answers. Each of
 * the four fields is checked on its own: the guard is one {@code ||} chain, so a test that only ever
 * passes a negative lobby would leave three quarters of it undefended — the same "every field looked
 * varied but only one was load-bearing" shape this stage's sweeps kept turning up.
 *
 * <p>The two {@code END} durations are deliberately different from each other everywhere below, for
 * the same reason: with one value for both, a record that returned the wrong one would be
 * indistinguishable from one that returned the right one.
 */
class RaceTimingsTest {

    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);
    private static final Duration TWO_MINUTES = Duration.ofMinutes(2);
    private static final Duration NEGATIVE = Duration.ofSeconds(-1);

    @Test
    void zeroLengthPhasesAreLegalBecauseTheBoundaryIsNegativityNotEmptiness() {
        RaceTimings instant = new RaceTimings(Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO);

        assertThat(instant.lobby()).isEqualTo(Duration.ZERO);
    }

    @Test
    void rejectsANegativeLobby() {
        assertThatThrownBy(() -> new RaceTimings(NEGATIVE, ONE_MINUTE, ONE_MINUTE, TWO_MINUTES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lobby=PT-1S");
    }

    @Test
    void rejectsANegativeRace() {
        assertThatThrownBy(() -> new RaceTimings(ONE_MINUTE, NEGATIVE, ONE_MINUTE, TWO_MINUTES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("race=PT-1S");
    }

    @Test
    void rejectsANegativeEndBetweenMaps() {
        assertThatThrownBy(() -> new RaceTimings(ONE_MINUTE, ONE_MINUTE, NEGATIVE, TWO_MINUTES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endBetweenMaps=PT-1S");
    }

    @Test
    void rejectsANegativeEndAfterTheLastMap() {
        assertThatThrownBy(() -> new RaceTimings(ONE_MINUTE, ONE_MINUTE, TWO_MINUTES, NEGATIVE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endAfterLastMap=PT-1S");
    }

    @Test
    void anotherMapFollowingPlaysTheBetweenMapsResultsScreen() {
        RaceTimings timings = new RaceTimings(ONE_MINUTE, ONE_MINUTE, Duration.ofSeconds(8),
                Duration.ofSeconds(20));

        assertThat(timings.end(true)).isEqualTo(Duration.ofSeconds(8));
    }

    @Test
    void theLastMapPlaysTheLongerOne() {
        RaceTimings timings = new RaceTimings(ONE_MINUTE, ONE_MINUTE, Duration.ofSeconds(8),
                Duration.ofSeconds(20));

        assertThat(timings.end(false)).isEqualTo(Duration.ofSeconds(20));
    }

    /**
     * The pacing decision itself, pinned. A cup is the unit a player experiences, and the old
     * numbers wrapped a minute of flying in 220 seconds of not playing with a results screen longer
     * than the race that earned it. A change to any of these four is a change to how the game feels
     * and should have to be made on purpose.
     */
    @Test
    void theDefaultsAreTheOnesThePacingPassChose() {
        assertThat(RaceTimings.DEFAULT.lobby()).isEqualTo(Duration.ofSeconds(20));
        assertThat(RaceTimings.DEFAULT.race()).isEqualTo(Duration.ofSeconds(300));
        assertThat(RaceTimings.DEFAULT.endBetweenMaps()).isEqualTo(Duration.ofSeconds(8));
        assertThat(RaceTimings.DEFAULT.endAfterLastMap()).isEqualTo(Duration.ofSeconds(20));
    }
}
