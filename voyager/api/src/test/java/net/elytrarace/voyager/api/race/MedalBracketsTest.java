package net.elytrarace.voyager.api.race;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MedalBracketsTest {

    private static final Duration REFERENCE = Duration.ofSeconds(60);

    @Test
    void classifiesEachBandAtItsBoundary() {
        // Every boundary is inclusive, so each of these lands on the tier named, not the next one
        // down. 60s x 1.00, 1.10, 1.25, 1.50.
        assertThat(MedalBrackets.DEFAULT.classify(Duration.ofSeconds(60), REFERENCE)).isEqualTo(MedalTier.DIAMOND);
        assertThat(MedalBrackets.DEFAULT.classify(Duration.ofSeconds(66), REFERENCE)).isEqualTo(MedalTier.GOLD);
        assertThat(MedalBrackets.DEFAULT.classify(Duration.ofSeconds(75), REFERENCE)).isEqualTo(MedalTier.SILVER);
        assertThat(MedalBrackets.DEFAULT.classify(Duration.ofSeconds(90), REFERENCE)).isEqualTo(MedalTier.BRONZE);
    }

    @Test
    void classifiesOneNanosecondPastEachBoundaryAsTheNextTierDown() {
        // A band that is only checked at its own boundary cannot tell <= from <.
        assertThat(MedalBrackets.DEFAULT.classify(Duration.ofSeconds(60).plusNanos(1), REFERENCE))
                .isEqualTo(MedalTier.GOLD);
        assertThat(MedalBrackets.DEFAULT.classify(Duration.ofSeconds(90).plusNanos(1), REFERENCE))
                .isEqualTo(MedalTier.FINISH);
    }

    @Test
    void classifiesAFastRunAsDiamond() {
        assertThat(MedalBrackets.DEFAULT.classify(Duration.ofSeconds(41), REFERENCE)).isEqualTo(MedalTier.DIAMOND);
    }

    @Test
    void neverReturnsDidNotFinish() {
        assertThat(MedalBrackets.DEFAULT.classify(Duration.ofHours(3), REFERENCE)).isEqualTo(MedalTier.FINISH);
    }

    @Test
    void rejectsBracketsThatAreNotStrictlyIncreasing() {
        assertThatThrownBy(() -> new MedalBrackets(1.10, 1.00, 1.25, 1.50))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scalesWithTheReferenceTimeRatherThanAssumingOne() {
        // A different reference must move every boundary. 30s x 1.25 = 37.5s.
        Duration shortMap = Duration.ofSeconds(30);

        assertThat(MedalBrackets.DEFAULT.classify(Duration.ofMillis(37_500), shortMap)).isEqualTo(MedalTier.SILVER);
        assertThat(MedalBrackets.DEFAULT.classify(Duration.ofMillis(37_501), shortMap)).isEqualTo(MedalTier.BRONZE);
    }
}
