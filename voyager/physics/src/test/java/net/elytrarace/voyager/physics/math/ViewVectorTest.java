package net.elytrarace.voyager.physics.math;

import net.elytrarace.voyager.api.math.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    /**
     * Guards against a silent {@code Math.*} substitution in the trigonometry: yaw 0 alone is
     * insufficient because {@code sin(0)} and {@code cos(0)} are identical in both the table and the
     * JDK, and with yaw 0 the x component becomes {@code ySin * xCos = 0 * xCos = 0} regardless of
     * source.
     *
     * <p>The pairs also guard the two degree-to-radian conversions on {@link ViewVector}'s first two
     * lines, which are the same {@code float} multiplication {@code StepContext} performs for its
     * lean angle and which {@code ElytraStepTest} already pins there. Substituting
     * {@code (float) Math.toRadians(...)} changes the {@code float} for about 9% of angles, but only
     * a small subset of those cross a table-index boundary and change the returned vector — and none
     * of the angles this module's tests otherwise use (0, ±5, ±12, ±12.5, 17, ±20, ±30, 37, ±45, 53,
     * 60, ±90) is in that subset. The first six pairs vary a pitch that is; the next five vary a yaw
     * that is; the last is the original non-degenerate pair.
     */
    @ParameterizedTest
    @CsvSource({
            "-89.599, 37.0", "-57.7771, 37.0", "-25.5542, 37.0",
            "17.1167, 37.0", "46.9281, 37.0", "79.953, 37.0",
            "17.0, -85.3418", "17.0, -46.2854", "17.0, 15.3479", "17.0, 72.724", "17.0, 136.9336",
            "17.0, 53.0",
    })
    void usesTheTableTrigonometryRatherThanTheJdk(float pitch, float yaw) {
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

    /**
     * States the property the pairs above rely on outright: at these angles the two conversions pick
     * different table entries, so a {@code Math.toRadians} substitution on either of {@link
     * ViewVector}'s two conversion lines produces a different vector. Without this, a later edit that
     * replaced the pairs with convenient round numbers would quietly restore the gap.
     */
    @ParameterizedTest
    @CsvSource({"-89.599, 37.0", "79.953, 37.0", "17.0, -85.3418", "17.0, 136.9336"})
    void theDegreeConversionDisagreesWithMathToRadiansAtTheseAngles(float pitch, float yaw) {
        float tablePitch = pitch * MinecraftMath.DEG_TO_RAD;
        float tableYaw = -yaw * MinecraftMath.DEG_TO_RAD;
        float jdkPitch = (float) Math.toRadians(pitch);
        float jdkYaw = (float) Math.toRadians(-yaw);

        boolean pitchDiffers = MinecraftMath.sin(tablePitch) != MinecraftMath.sin(jdkPitch)
                || MinecraftMath.cos(tablePitch) != MinecraftMath.cos(jdkPitch);
        boolean yawDiffers = MinecraftMath.sin(tableYaw) != MinecraftMath.sin(jdkYaw)
                || MinecraftMath.cos(tableYaw) != MinecraftMath.cos(jdkYaw);

        assertThat(pitchDiffers || yawDiffers)
                .as("pitch=%s yaw=%s must land on a table-index boundary".formatted(pitch, yaw))
                .isTrue();
    }
}
