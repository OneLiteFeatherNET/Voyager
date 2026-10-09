package net.elytrarace.voyager.api.race;

import net.elytrarace.voyager.api.race.exception.InvalidBoostConfigException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoostConfigTest {

    @Test
    void keepsWhatItWasGiven() {
        // 18 and 41: not equal, not a multiple of one another, and neither is the Vanilla default —
        // a record that swapped its two components, or ignored one of them, changes one of these.
        BoostConfig config = new BoostConfig(18, 41);

        assertThat(config.burnDurationTicks()).isEqualTo(18);
        assertThat(config.cooldownTicks()).isEqualTo(41);
    }

    @Test
    void refusesABurnOfZeroOrLess() {
        assertThatThrownBy(() -> new BoostConfig(0, 40))
                .isInstanceOf(InvalidBoostConfigException.class)
                .hasMessageContaining("burn duration must be positive");
        assertThatThrownBy(() -> new BoostConfig(-1, 40))
                .isInstanceOf(InvalidBoostConfigException.class);
    }

    /**
     * The invariant this type exists to enforce, and the reason it is checked here rather than left
     * to whoever edits a map file: with a cooldown no longer than the burn, one racer can hold two
     * burning rockets, and {@code FlightInput.fireworkBoostActive} is a boolean that cannot say so.
     * The simulation would apply one impulse where Vanilla applied two and stay self-consistently
     * wrong.
     */
    @Test
    void refusesACooldownThatDoesNotOutlastTheBurn() {
        assertThatThrownBy(() -> new BoostConfig(30, 30))
                .isInstanceOf(InvalidBoostConfigException.class)
                .hasMessageContaining("cannot express");
        assertThatThrownBy(() -> new BoostConfig(30, 29))
                .isInstanceOf(InvalidBoostConfigException.class);
        // One tick longer is the smallest acceptable answer, and it has to be accepted: the rule is
        // "strictly longer", not "comfortably longer".
        assertThatCode(() -> new BoostConfig(30, 31)).doesNotThrowAnyException();
    }

    /**
     * The seed the converter writes when an old map file carries no burn of its own. Pinned so that
     * changing it is a visible edit to a test rather than a number that moved: it is
     * {@code 10 * flightDuration} for Vanilla's strongest rocket, and the same three the item handed
     * to a racer declares.
     */
    @Test
    void theVanillaDerivedBurnIsThirtyTicks() {
        assertThat(BoostConfig.VANILLA_BURN_TICKS).isEqualTo(30);
    }

    /**
     * The real map's own cooldown of 2000 ms is 40 ticks, which is longer than the derived burn of 30
     * — so the two values the committed course is built from can actually be held together. Asserted
     * because the pairing is a coincidence of two independent sources, not a guarantee: a course
     * whose old file said 1000 ms would fail to convert, and this is where that is visible.
     */
    @Test
    void theCommittedCoursesOwnCooldownOutlastsTheDerivedBurn() {
        assertThatCode(() -> new BoostConfig(BoostConfig.VANILLA_BURN_TICKS, 40))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> new BoostConfig(BoostConfig.VANILLA_BURN_TICKS, 20))
                .isInstanceOf(InvalidBoostConfigException.class);
    }
}
