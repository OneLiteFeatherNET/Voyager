package net.elytrarace.voyager.race.standing;

import net.elytrarace.voyager.race.standing.PersonalBestComparison.Outcome;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** The finish against the stored best of the same map: three outcomes and the delta to the best. */
class PersonalBestComparisonTest {

    @Test
    void aFasterRunIsANewBestWithItsDelta() {
        PersonalBestComparison compared = PersonalBestComparison.of(Duration.ofMillis(59_100), Duration.ofMillis(61_200));

        assertThat(compared.outcome()).isEqualTo(Outcome.NEW_BEST);
        assertThat(compared.delta())
                .as("61.2 s stored, 59.1 s finished: 2.1 s faster")
                .isEqualTo(Duration.ofMillis(2_100));
    }

    @Test
    void aSlowerRunIsTheDeltaToTheBest() {
        PersonalBestComparison compared = PersonalBestComparison.of(Duration.ofMillis(61_800), Duration.ofMillis(61_200));

        assertThat(compared.outcome()).isEqualTo(Outcome.SLOWER);
        assertThat(compared.delta())
                .as("61.8 s finished against 61.2 s stored: 0.6 s to the best")
                .isEqualTo(Duration.ofMillis(600));
    }

    @Test
    void anEqualRunIsEqualWithNoDelta() {
        PersonalBestComparison compared = PersonalBestComparison.of(Duration.ofMillis(61_200), Duration.ofMillis(61_200));

        assertThat(compared.outcome()).isEqualTo(Outcome.EQUAL);
        assertThat(compared.delta()).isEqualTo(Duration.ZERO);
    }

    @Test
    void noStoredBestIsAFirstRunWithNoDelta() {
        PersonalBestComparison compared = PersonalBestComparison.of(Duration.ofMillis(61_200), null);

        assertThat(compared.outcome()).isEqualTo(Outcome.FIRST_RUN);
        assertThat(compared.delta())
                .as("a first run has no best to measure from")
                .isEqualTo(Duration.ZERO);
    }
}
