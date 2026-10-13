package net.elytrarace.voyager.race.com;

import net.elytrarace.voyager.api.race.com.ComDifficulty;
import net.elytrarace.voyager.race.com.exception.InvalidComTuningException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The four knobs of a difficulty, and the rule that keeps each computer racer at or below the reference speed.
 *
 * <p>The numbers under test are the table in the design (D3), with MEDIUM as the default. The seed is a fixed
 * number, so every assertion gives the same answer on every run.
 */
class ComTuningTest {

    private static final long SEED = 42L;

    @Test
    void easyIsSlowerAndWandersFurtherThanMedium() {
        ComTuning easy = ComTuning.of(ComDifficulty.EASY, SEED);
        ComTuning medium = ComTuning.of(ComDifficulty.MEDIUM, SEED);

        assertThat(easy.speedFactor())
                .as("the EASY speed factor, which is the share of the reference speed")
                .isLessThan(medium.speedFactor());
        assertThat(easy.lineOffsetAmplitude())
                .as("the EASY line offset amplitude, in blocks")
                .isGreaterThan(medium.lineOffsetAmplitude());
    }

    @Test
    void hardHasNoMissChanceAndTheReferenceSpeedFactor() {
        ComTuning hard = ComTuning.of(ComDifficulty.HARD, SEED);

        assertThat(hard.missChance())
                .as("the HARD miss chance per ring")
                .isZero();
        assertThat(hard.speedFactor())
                .as("the HARD speed factor, which is the reference racer")
                .isEqualTo(1.0);
    }

    @Test
    void theSpeedFactorNeverExceedsOneForAnyDifficultyOrSeed() {
        for (ComDifficulty difficulty : ComDifficulty.values()) {
            for (long seed = 0; seed < 200; seed++) {
                ComTuning tuning = ComTuning.of(difficulty, seed);

                assertThat(tuning.effectiveSpeedFactor())
                        .as("the effective speed factor for %s with seed %s".formatted(difficulty, seed))
                        .isLessThanOrEqualTo(1.0);
            }
        }
    }

    @Test
    void thePersonalVariationStaysWithinTwoPerCent() {
        for (long seed = 0; seed < 200; seed++) {
            assertThat(ComTuning.of(ComDifficulty.MEDIUM, seed).personalSpeed())
                    .as("the personal speed for seed %s".formatted(seed))
                    .isBetween(0.98, 1.02);
        }
    }

    @Test
    void aNegativeAmplitudeIsRefused() {
        assertThatThrownBy(() -> new ComTuning(0.93, 1.0, -0.1, 60.0, 0.02, 4))
                .isInstanceOf(InvalidComTuningException.class)
                .hasMessageContaining("-0.1");
    }
}
