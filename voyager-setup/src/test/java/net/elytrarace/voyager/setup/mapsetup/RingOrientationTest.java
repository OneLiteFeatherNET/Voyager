package net.elytrarace.voyager.setup.mapsetup;

import net.elytrarace.voyager.api.math.Vec3;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RingOrientationTest {

    private static final double TOLERANCE = 1e-9;

    static Stream<Vec3> normals() {
        return Stream.of(new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1), new Vec3(0, 0, -1),
                new Vec3(0.6, 0.0, 0.8));
    }

    @ParameterizedTest
    @MethodSource("normals")
    void theBaseAxisIsRotatedOntoTheNormal(Vec3 normal) {
        RingOrientation.Quaternion rotation = RingOrientation.fromNormal(normal);

        Vec3 rotated = RingOrientation.rotate(rotation, RingOrientation.BASE_AXIS);

        assertThat(rotated.x()).isCloseTo(normal.x(), within(TOLERANCE));
        assertThat(rotated.y()).isCloseTo(normal.y(), within(TOLERANCE));
        assertThat(rotated.z()).isCloseTo(normal.z(), within(TOLERANCE));
    }

    @ParameterizedTest
    @MethodSource("normals")
    void theRotationIsAUnitQuaternion(Vec3 normal) {
        RingOrientation.Quaternion rotation = RingOrientation.fromNormal(normal);

        double length = Math.sqrt(rotation.x() * rotation.x() + rotation.y() * rotation.y()
                + rotation.z() * rotation.z() + rotation.w() * rotation.w());

        assertThat(length).isCloseTo(1.0, within(TOLERANCE));
    }

    @ParameterizedTest
    @MethodSource("normals")
    void theRotationIsThePositionOfTheBaseAxisNotItsOpposite(Vec3 normal) {
        Vec3 opposite = RingOrientation.rotate(RingOrientation.fromNormal(normal), RingOrientation.BASE_AXIS.scale(-1));

        assertThat(opposite.dot(normal)).isCloseTo(-1.0, within(TOLERANCE));
    }

    @ParameterizedTest
    @MethodSource("normals")
    void theDiscOffsetPutsTheMiddleOfTheDiscAtTheOrigin(Vec3 normal) {
        RingOrientation.Quaternion rotation = RingOrientation.fromNormal(normal);
        double radius = 3.0;
        double thickness = 0.05;

        Vec3 offset = RingOrientation.discOffset(rotation, radius, thickness);
        Vec3 middle = RingOrientation.rotate(rotation, new Vec3(radius, radius, thickness / 2));

        assertThat(offset.x() + middle.x()).isCloseTo(0.0, within(TOLERANCE));
        assertThat(offset.y() + middle.y()).isCloseTo(0.0, within(TOLERANCE));
        assertThat(offset.z() + middle.z()).isCloseTo(0.0, within(TOLERANCE));
    }
}
