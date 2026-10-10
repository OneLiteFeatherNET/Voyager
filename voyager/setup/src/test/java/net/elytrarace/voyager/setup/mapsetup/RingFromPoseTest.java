package net.elytrarace.voyager.setup.mapsetup;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.setup.mapsetup.exception.InvalidPoseException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class RingFromPoseTest {

    @Test
    void theEyePositionBecomesTheCentreOfTheRing() {
        Ring ring = RingFromPose.create(new Vec3(10.0, 65.6, 4.0), new Vec3(1, 0, 0), 2);

        assertThat(ring.center()).isEqualTo(new Vec3(10.0, 65.6, 4.0));
        assertThat(ring.index()).isEqualTo(2);
        assertThat(ring.radius()).isEqualTo(RingDefaults.RADIUS);
        assertThat(ring.points()).isEqualTo(RingDefaults.POINTS);
        assertThat(ring.type()).isEqualTo(RingType.STANDARD);
    }

    @Test
    void aLookOfLengthTwoIsNormalisedAndKeepsItsDirection() {
        Ring ring = RingFromPose.create(Vec3.ZERO, new Vec3(0, 2, 0), 0);

        assertThat(ring.normal().x()).isCloseTo(0.0, within(1e-12));
        assertThat(ring.normal().y()).isCloseTo(1.0, within(1e-12));
        assertThat(ring.normal().z()).isCloseTo(0.0, within(1e-12));
    }

    @Test
    void aZeroLookIsRefused() {
        assertThatThrownBy(() -> RingFromPose.create(Vec3.ZERO, Vec3.ZERO, 0))
                .isInstanceOf(InvalidPoseException.class);
    }

    @Test
    void aLookWhoseLengthOverflowsIsRefused() {
        assertThatThrownBy(() -> RingFromPose.create(Vec3.ZERO, new Vec3(1e200, 1e200, 0), 0))
                .isInstanceOf(InvalidPoseException.class);
    }
}
