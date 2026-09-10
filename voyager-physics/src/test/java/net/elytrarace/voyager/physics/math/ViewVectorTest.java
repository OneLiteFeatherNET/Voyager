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
        // Guard against silent Math.* substitution: yaw=0 alone is insufficient because
        // sin(0) and cos(0) are identical in both table and JDK implementations.
        // With yaw=0, the x component becomes ySin*xCos = 0*xCos = 0 regardless of source.
        // Use non-degenerate angles and verify all three components exactly.
        float pitch = 17.0f;
        float yaw = 53.0f;
        float realXRot = pitch * MinecraftMath.DEG_TO_RAD;
        float realYRot = -yaw * MinecraftMath.DEG_TO_RAD;
        float yCos = MinecraftMath.cos(realYRot);
        float ySin = MinecraftMath.sin(realYRot);
        float xCos = MinecraftMath.cos(realXRot);
        float xSin = MinecraftMath.sin(realXRot);

        Vec3 look = ViewVector.of(pitch, yaw);
        assertThat(look.x()).isEqualTo((double) (ySin * xCos));
        assertThat(look.y()).isEqualTo((double) (-xSin));
        assertThat(look.z()).isEqualTo((double) (yCos * xCos));
    }
}
