package net.elytrarace.voyager.api.race.effect;

import net.elytrarace.voyager.api.race.effect.exception.InvalidRingEffectException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RingEffectTuningTest {

    @Test
    void theDefaultTuningIsOneAndAHalfForBoostAndOneHalfForSlow() {
        assertThat(RingEffectTuning.DEFAULT.boostMultiplier()).isEqualTo(1.5);
        assertThat(RingEffectTuning.DEFAULT.slowMultiplier()).isEqualTo(0.5);
    }

    @Test
    void aNegativeFactorIsRefusedAndTheMessageNamesTheField() {
        assertThatThrownBy(() -> new RingEffectTuning(-1.0, 0.5))
                .isInstanceOf(InvalidRingEffectException.class)
                .hasMessageContaining("boostMultiplier");
        assertThatThrownBy(() -> new RingEffectTuning(1.5, -0.25))
                .isInstanceOf(InvalidRingEffectException.class)
                .hasMessageContaining("slowMultiplier");
    }

    @Test
    void aNonFiniteFactorIsRefused() {
        assertThatThrownBy(() -> new RingEffectTuning(Double.NaN, 0.5))
                .isInstanceOf(InvalidRingEffectException.class)
                .hasMessageContaining("boostMultiplier");
        assertThatThrownBy(() -> new RingEffectTuning(1.5, Double.POSITIVE_INFINITY))
                .isInstanceOf(InvalidRingEffectException.class)
                .hasMessageContaining("slowMultiplier");
    }

    /** Zero is a legal factor: a ring that stops a racer dead is a strange design, not an impossible value. */
    @Test
    void zeroIsAcceptedAsAFactor() {
        assertThatCode(() -> new RingEffectTuning(0.0, 0.0)).doesNotThrowAnyException();
    }
}
