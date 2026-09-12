package net.elytrarace.voyager.race.scoring;

import net.elytrarace.voyager.api.race.MedalTier;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The outlook's invariants, which exist so that a caller cannot be handed a value that says two
 * things at once — a band with no countdown and no reason to have none, or a countdown attached to
 * the band below every bracket.
 */
class MedalOutlookTest {

    @Test
    void aBandedTierCarriesItsCountdown() {
        MedalOutlook outlook = new MedalOutlook(MedalTier.GOLD, Optional.of(Duration.ofSeconds(6)));

        assertThat(outlook.tier()).isEqualTo(MedalTier.GOLD);
        assertThat(outlook.untilLost()).contains(Duration.ofSeconds(6));
    }

    @Test
    void finishCarriesNoneBecauseThereIsNothingUnderIt() {
        MedalOutlook outlook = new MedalOutlook(MedalTier.FINISH, Optional.empty());

        assertThat(outlook.untilLost()).isEmpty();
    }

    @Test
    void aBandedTierWithoutACountdownIsRefused() {
        assertThatThrownBy(() -> new MedalOutlook(MedalTier.BRONZE, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BRONZE");
    }

    @Test
    void finishWithACountdownIsRefused() {
        assertThatThrownBy(() -> new MedalOutlook(MedalTier.FINISH, Optional.of(Duration.ofSeconds(1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FINISH");
    }

    @Test
    void aBandAlreadyLostIsADifferentBandAndNotANegativeCountdown() {
        assertThatThrownBy(() -> new MedalOutlook(MedalTier.SILVER, Optional.of(Duration.ofMillis(-1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void dnfIsNotABandARunningClockIsInside() {
        assertThatThrownBy(() -> new MedalOutlook(MedalTier.DNF, Optional.of(Duration.ofSeconds(1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DNF");
        assertThatThrownBy(() -> new MedalOutlook(MedalTier.DNF, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DNF");
    }
}
