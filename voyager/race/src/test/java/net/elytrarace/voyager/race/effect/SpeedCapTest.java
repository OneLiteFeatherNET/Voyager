package net.elytrarace.voyager.race.effect;

import net.elytrarace.voyager.api.math.Vec3;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SpeedCapTest {

    private static final double TOLERANCE = 1e-12;

    @Test
    void aVelocityUnderTheCapIsUnchanged() {
        Vec3 velocity = new Vec3(0.0, 0.0, 2.0);

        assertThat(SpeedCap.limit(velocity, 3.5)).isEqualTo(velocity);
    }

    /** A 3-4-5 velocity capped at 2.5 keeps its direction and has the cap's length: (1.5, 0, 2.0). */
    @Test
    void aVelocityOverTheCapKeepsItsDirectionAndHasTheCapsLength() {
        Vec3 limited = SpeedCap.limit(new Vec3(3.0, 0.0, 4.0), 2.5);

        assertThat(limited.x()).isCloseTo(1.5, within(TOLERANCE));
        assertThat(limited.y()).isCloseTo(0.0, within(TOLERANCE));
        assertThat(limited.z()).isCloseTo(2.0, within(TOLERANCE));
        assertThat(limited.length()).isCloseTo(2.5, within(TOLERANCE));
    }

    @Test
    void noCapLeavesAVelocityUnchangedHoweverLarge() {
        Vec3 velocity = new Vec3(0.0, 0.0, 9.0);

        assertThat(SpeedCap.limit(velocity, null)).isEqualTo(velocity);
    }

    @Test
    void theZeroVelocityIsUnchangedUnderACap() {
        assertThat(SpeedCap.limit(Vec3.ZERO, 3.5)).isEqualTo(Vec3.ZERO);
    }
}
