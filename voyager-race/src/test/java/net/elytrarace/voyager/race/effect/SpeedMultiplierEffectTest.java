package net.elytrarace.voyager.race.effect;

import net.elytrarace.voyager.api.math.Vec3;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class SpeedMultiplierEffectTest {

    private static final Vec3 VELOCITY = new Vec3(0.75, -0.32, 1.6);
    private static final double TOLERANCE = 1e-12;

    @Test
    void scalesEveryComponentByTheFactorItWasGiven() {
        // A factor neither of the two the registry uses, so this cannot pass by matching one of them.
        Vec3 result = new SpeedMultiplierEffect(2.5).apply(VELOCITY);

        assertThat(result.x()).isCloseTo(1.875, within(TOLERANCE));
        assertThat(result.y()).isCloseTo(-0.8, within(TOLERANCE));
        assertThat(result.z()).isCloseTo(4.0, within(TOLERANCE));
    }

    /**
     * Zero is a legal factor: a ring that stops a player dead is a strange design choice, not an
     * impossible value.
     *
     * <p>Asserted component by component rather than against {@link Vec3#ZERO}, because
     * {@code -0.32 * 0.0} is {@code -0.0} and a record's generated {@code equals} compares doubles
     * bitwise — so the resulting vector is numerically zero but not {@code equals} to
     * {@code Vec3.ZERO}. Worth knowing about anywhere a velocity is compared for equality; it is
     * {@code Vec3}'s business rather than this class's, and is recorded in the fix round's report.
     */
    @Test
    void aZeroFactorIsAllowedAndStopsThePlayerDead() {
        Vec3 stopped = new SpeedMultiplierEffect(0.0).apply(VELOCITY);

        assertThat(stopped.x()).isZero();
        assertThat(stopped.y()).isZero();
        assertThat(stopped.z()).isZero();
    }

    @Test
    void rejectsANegativeFactorBecauseItWouldReverseThePlayerRatherThanSlowThem() {
        assertThatThrownBy(() -> new SpeedMultiplierEffect(-0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void rejectsANonFiniteFactorHereRatherThanLettingVec3RejectTheResult() {
        assertThatThrownBy(() -> new SpeedMultiplierEffect(Double.NaN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("finite");
        assertThatThrownBy(() -> new SpeedMultiplierEffect(Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("finite");
    }
}
