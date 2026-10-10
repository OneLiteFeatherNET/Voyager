package net.elytrarace.voyager.race.scoring;

import net.elytrarace.voyager.api.race.MedalBrackets;
import net.elytrarace.voyager.api.race.MedalTier;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The medal countdown, which is the only number on the boss bar and has to be exactly right.
 *
 * <h2>Why the fixtures look like this</h2>
 *
 * <p>The reference time is 60 s and the brackets are the committed 1.00 / 1.10 / 1.25 / 1.50, so the
 * four boundaries land on 60.0 / 66.0 / 75.0 / 90.0 s — four values that are all different from each
 * other and none of which is a multiple of another. A countdown computed from the wrong bracket
 * therefore lands on a different number for every band rather than accidentally agreeing on one.
 *
 * <p>Every band is tested on both sides of its own boundary, one millisecond apart. That is the
 * assertion the whole class exists for: the band the bar showed on the last tick of a race and the
 * medal the results screen awards come from the same classifier, and a boundary that disagreed by a
 * tick would show a racer diamond and then award them gold.
 */
class MedalCountdownTest {

    private static final Duration REFERENCE = Duration.ofSeconds(60);
    private static final MedalBrackets BRACKETS = MedalBrackets.DEFAULT;

    private static MedalOutlook at(String seconds) {
        return MedalCountdown.outlook(Duration.ofMillis(Math.round(Double.parseDouble(seconds) * 1000)),
                REFERENCE, BRACKETS);
    }

    @Test
    void aFreshClockIsInsideDiamondWithTheWholeReferenceTimeToSpend() {
        MedalOutlook outlook = at("0");

        assertThat(outlook.tier()).isEqualTo(MedalTier.DIAMOND);
        assertThat(outlook.untilLost()).contains(Duration.ofSeconds(60));
    }

    @Test
    void theCountdownIsTheSubtractionAndNotAnEstimate() {
        MedalOutlook outlook = at("23.4");

        assertThat(outlook.tier()).isEqualTo(MedalTier.DIAMOND);
        assertThat(outlook.untilLost()).contains(Duration.ofMillis(36_600));
    }

    @Test
    void diamondIsStillHeldOnItsOwnBoundaryWithNothingLeft() {
        MedalOutlook outlook = at("60");

        assertThat(outlook.tier()).isEqualTo(MedalTier.DIAMOND);
        assertThat(outlook.untilLost()).contains(Duration.ZERO);
    }

    @Test
    void oneMillisecondPastTheBoundaryIsGoldAndTheCountdownRestartsAgainstGoldsOwnLimit() {
        MedalOutlook outlook = MedalCountdown.outlook(
                Duration.ofMillis(60_001), REFERENCE, BRACKETS);

        assertThat(outlook.tier()).isEqualTo(MedalTier.GOLD);
        assertThat(outlook.untilLost()).contains(Duration.ofMillis(5_999));
    }

    @Test
    void goldIsHeldToSixtySixSecondsAndSilverToSeventyFive() {
        assertThat(at("66").tier()).isEqualTo(MedalTier.GOLD);
        assertThat(at("66").untilLost()).contains(Duration.ZERO);

        assertThat(at("66.001").tier()).isEqualTo(MedalTier.SILVER);
        assertThat(at("66.001").untilLost()).contains(Duration.ofMillis(8_999));

        assertThat(at("75").tier()).isEqualTo(MedalTier.SILVER);
        assertThat(at("75").untilLost()).contains(Duration.ZERO);
    }

    @Test
    void bronzeIsHeldToNinetySeconds() {
        assertThat(at("75.001").tier()).isEqualTo(MedalTier.BRONZE);
        assertThat(at("75.001").untilLost()).contains(Duration.ofMillis(14_999));

        assertThat(at("90").tier()).isEqualTo(MedalTier.BRONZE);
        assertThat(at("90").untilLost()).contains(Duration.ZERO);
    }

    @Test
    void pastEveryBracketThereIsOnlyAFinishAndNothingLeftToLose() {
        MedalOutlook outlook = at("90.001");

        assertThat(outlook.tier()).isEqualTo(MedalTier.FINISH);
        assertThat(outlook.untilLost()).isEmpty();
    }

    /**
     * The brackets scale with the map, which is the whole reason they are multipliers. A ten-second
     * course loses diamond at ten seconds, not at sixty.
     */
    @Test
    void theBoundariesFollowTheMapsOwnReferenceTime() {
        Duration shortCourse = Duration.ofSeconds(10);

        MedalOutlook outlook = MedalCountdown.outlook(Duration.ofSeconds(4), shortCourse, BRACKETS);

        assertThat(outlook.tier()).isEqualTo(MedalTier.DIAMOND);
        assertThat(outlook.untilLost()).contains(Duration.ofSeconds(6));
    }

    /**
     * Non-default brackets, so nothing in this class can be satisfied by a constant. A course with
     * a generous diamond band loses it later, and the countdown says so.
     */
    @Test
    void aDifferentBracketSetMovesEveryBoundaryWithIt() {
        MedalBrackets generous = new MedalBrackets(1.50, 1.75, 2.00, 3.00);

        MedalOutlook outlook = MedalCountdown.outlook(Duration.ofSeconds(60), REFERENCE, generous);

        assertThat(outlook.tier()).isEqualTo(MedalTier.DIAMOND);
        assertThat(outlook.untilLost()).contains(Duration.ofSeconds(30));
    }

    @Test
    void everyBracketedBandHasItsOwnBoundaryOnTheClock() {
        assertThat(MedalCountdown.boundaryOf(MedalTier.DIAMOND, REFERENCE, BRACKETS))
                .isEqualTo(Duration.ofSeconds(60));
        assertThat(MedalCountdown.boundaryOf(MedalTier.GOLD, REFERENCE, BRACKETS))
                .isEqualTo(Duration.ofSeconds(66));
        assertThat(MedalCountdown.boundaryOf(MedalTier.SILVER, REFERENCE, BRACKETS))
                .isEqualTo(Duration.ofSeconds(75));
        assertThat(MedalCountdown.boundaryOf(MedalTier.BRONZE, REFERENCE, BRACKETS))
                .isEqualTo(Duration.ofSeconds(90));
    }

    @Test
    void theTwoTiersThatAreNotBandsHaveNoBoundaryToAskFor() {
        assertThatThrownBy(() -> MedalCountdown.boundaryOf(MedalTier.FINISH, REFERENCE, BRACKETS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FINISH");
        assertThatThrownBy(() -> MedalCountdown.boundaryOf(MedalTier.DNF, REFERENCE, BRACKETS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DNF");
    }

    /**
     * The property the countdown is worth having at all: the boundary it counts down to is the last
     * instant the classifier still awards that medal. Anything else and the bar lies on its last
     * tick.
     */
    @Test
    void theBoundaryIsTheLastInstantTheClassifierStillAwardsThatMedal() {
        for (MedalTier tier : new MedalTier[] {
                MedalTier.DIAMOND, MedalTier.GOLD, MedalTier.SILVER, MedalTier.BRONZE }) {
            Duration boundary = MedalCountdown.boundaryOf(tier, REFERENCE, BRACKETS);

            assertThat(BRACKETS.classify(boundary, REFERENCE))
                    .as("%s still holds on its own boundary", tier)
                    .isEqualTo(tier);
            assertThat(BRACKETS.classify(boundary.plusNanos(1), REFERENCE))
                    .as("%s is gone one nanosecond later", tier)
                    .isNotEqualTo(tier);
        }
    }

    @Test
    void anOutlookNeverCarriesANegativeCountdown() {
        assertThat(at("59.999").untilLost()).contains(Duration.ofMillis(1));
        assertThat(at("89.999").untilLost().orElseThrow()).isGreaterThanOrEqualTo(Duration.ZERO);
        assertThat(at("120").untilLost()).isEmpty();
    }
}
