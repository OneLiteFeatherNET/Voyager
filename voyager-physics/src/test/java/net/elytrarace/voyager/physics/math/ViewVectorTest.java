package net.elytrarace.voyager.physics.math;

import net.elytrarace.voyager.api.math.Vec3;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ViewVectorTest {

    @Test
    void lookingStraightAheadAtZeroYawPointsAlongPositiveZ() {
        Vec3 look = ViewVector.of(0.0f, 0.0f);

        assertThat(look.x()).isCloseTo(0.0, within(1.0e-3));
        assertThat(look.y()).isCloseTo(0.0, within(1.0e-3));
        assertThat(look.z()).isCloseTo(1.0, within(1.0e-3));
    }

    @Test
    void yawOfNinetyPointsAlongNegativeX() {
        Vec3 look = ViewVector.of(0.0f, 90.0f);

        assertThat(look.x()).isCloseTo(-1.0, within(1.0e-3));
        assertThat(look.z()).isCloseTo(0.0, within(1.0e-3));
    }

    @Test
    void negativePitchPointsUpwards() {
        assertThat(ViewVector.of(-45.0f, 0.0f).y()).isGreaterThan(0.0);
    }

    @Test
    void positivePitchPointsDownwards() {
        assertThat(ViewVector.of(45.0f, 0.0f).y()).isLessThan(0.0);
    }

    @Test
    void isApproximatelyUnitLength() {
        for (float pitch = -90.0f; pitch <= 90.0f; pitch += 7.5f) {
            for (float yaw = -180.0f; yaw < 180.0f; yaw += 15.0f) {
                assertThat(ViewVector.of(pitch, yaw).length())
                        .as("pitch=%s yaw=%s".formatted(pitch, yaw))
                        .isCloseTo(1.0, within(1.0e-3));
            }
        }
    }

    @Test
    void straightDownIsAlmostExactlyNegativeY() {
        Vec3 look = ViewVector.of(90.0f, 0.0f);

        assertThat(look.y()).isCloseTo(-1.0, within(1.0e-3));
        assertThat(Math.hypot(look.x(), look.z())).isCloseTo(0.0, within(1.0e-3));
    }

    @Test
    void usesTheTableTrigonometryRatherThanTheJdk() {
        // Same guard as MinecraftMathTest, one level up: a view vector built from Math.* would be
        // close enough to pass every assertion above and still drift against the traces.
        float pitch = 17.0f;
        float leanAngle = pitch * MinecraftMath.DEG_TO_RAD;
        double expectedY = -MinecraftMath.sin(leanAngle);

        assertThat(ViewVector.of(pitch, 0.0f).y()).isEqualTo(expectedY);
    }
}
