package net.elytrarace.voyager.race.flow;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The negativity guard, which nothing exercised until the final review deleted it and watched the
 * suite stay green. Each of the three fields is checked on its own: the guard is one {@code ||}
 * chain, so a test that only ever passes a negative lobby would leave two thirds of it undefended —
 * the same "every field looked varied but only one was load-bearing" shape this stage's sweeps kept
 * turning up.
 */
class RaceTimingsTest {

    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);
    private static final Duration NEGATIVE = Duration.ofSeconds(-1);

    @Test
    void zeroLengthPhasesAreLegalBecauseTheBoundaryIsNegativityNotEmptiness() {
        RaceTimings instant = new RaceTimings(Duration.ZERO, Duration.ZERO, Duration.ZERO);

        assertThat(instant.lobby()).isEqualTo(Duration.ZERO);
    }

    @Test
    void rejectsANegativeLobby() {
        assertThatThrownBy(() -> new RaceTimings(NEGATIVE, ONE_MINUTE, ONE_MINUTE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lobby=PT-1S");
    }

    @Test
    void rejectsANegativeRace() {
        assertThatThrownBy(() -> new RaceTimings(ONE_MINUTE, NEGATIVE, ONE_MINUTE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("race=PT-1S");
    }

    @Test
    void rejectsANegativeEnd() {
        assertThatThrownBy(() -> new RaceTimings(ONE_MINUTE, ONE_MINUTE, NEGATIVE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("end=PT-1S");
    }
}
