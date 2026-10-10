package net.elytrarace.tools.converter;

import net.elytrarace.tools.converter.exception.InvalidCourseException;
import net.elytrarace.voyager.api.math.Vec3;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RingGeometryTest {

    // (1, 2, 2) and (2, 1, -2) are orthogonal, both exactly 3 long, and their plane's normal
    // (-2, 2, -1)/3 points along no axis. A rim built from them is the case an axis-aligned fixture
    // cannot distinguish: every axis-aligned ring has two of its normal's three components at zero,
    // where a wrong component is invisible.
    private static final Vec3 TILTED_FIRST = new Vec3(1, 2, 2);
    private static final Vec3 TILTED_SECOND = new Vec3(2, 1, -2);
    private static final Vec3 TILTED_NORMAL = new Vec3(-2.0 / 3.0, 2.0 / 3.0, -1.0 / 3.0);

    @Test
    void axisIsDerivedEvenWhenTheFirstTwoRimPointsAreDiametricallyOpposite() {
        Vec3 center = new Vec3(-40, 71, 12);
        List<Vec3> rim = List.of(
                center.plus(TILTED_FIRST),
                center.minus(TILTED_FIRST),
                center.plus(TILTED_SECOND),
                center.minus(TILTED_SECOND));

        Vec3 axis = RingGeometry.axis(center, rim);

        // The first pair crosses to exactly zero, so a derivation that stopped there would have had
        // to invent something. Asserting the actual direction rather than "not null" is what makes
        // the invented default (0, 0, 1) a failure here.
        assertThat(Math.abs(axis.dot(TILTED_NORMAL))).isCloseTo(1.0, Offset.offset(1e-12));
        assertThat(axis.length()).isCloseTo(1.0, Offset.offset(1e-12));
    }

    @Test
    void axisIsPerpendicularToEveryRimVectorOfAnEightPointRing() {
        Vec3 center = new Vec3(85, -54, 54);
        List<Vec3> rim = rim(center, LegacyPortals.octagon(new int[] {0, 1, 0}, new int[] {0, 0, 1}, 2, 3));

        Vec3 axis = RingGeometry.axis(center, rim);

        assertThat(Math.abs(axis.dot(new Vec3(1, 0, 0)))).isCloseTo(1.0, Offset.offset(1e-12));
        for (Vec3 point : rim) {
            assertThat(point.minus(center).dot(axis)).isCloseTo(0.0, Offset.offset(1e-12));
        }
    }

    @Test
    void axisRejectsARimWhoseEveryPairIsCollinearWithTheCentre() {
        Vec3 center = new Vec3(6, -3, 200);
        List<Vec3> rim = List.of(
                center.plus(TILTED_FIRST),
                center.minus(TILTED_FIRST),
                center.plus(TILTED_FIRST.scale(2)));

        assertThatThrownBy(() -> RingGeometry.axis(center, rim))
                .isInstanceOf(InvalidCourseException.class)
                .hasMessageContaining("span a plane");
    }

    @Test
    void axisRejectsARimPointThatDoesNotLieInThePlaneOfTheOthers() {
        Vec3 center = new Vec3(0, 90, -17);
        List<Vec3> rim = List.of(
                center.plus(TILTED_FIRST),
                center.plus(TILTED_SECOND),
                center.minus(TILTED_FIRST),
                center.plus(new Vec3(-2, 2, -1)));

        assertThatThrownBy(() -> RingGeometry.axis(center, rim))
                .isInstanceOf(InvalidCourseException.class)
                .hasMessageContaining("off the plane");
    }

    @Test
    void axisRejectsFewerThanTwoRimPoints() {
        Vec3 center = new Vec3(3, 3, 3);

        assertThatThrownBy(() -> RingGeometry.axis(center, List.of(center.plus(TILTED_FIRST))))
                .isInstanceOf(InvalidCourseException.class)
                .hasMessageContaining("at least two rim points");
    }

    @Test
    void radiusIsTheSharedDistanceFromTheCentreToTheRim() {
        Vec3 center = new Vec3(85, -54, 54);
        List<Vec3> rim = rim(center, LegacyPortals.octagon(new int[] {0, 1, 0}, new int[] {0, 0, 1}, 2, 3));

        // The shipped course measures exactly this on all 35 of its rings.
        assertThat(RingGeometry.radius(center, rim))
                .isCloseTo(Math.sqrt(13), Offset.offset(1e-12));
    }

    @Test
    void radiusIsNotTheLargestDistanceWhenTheRimDisagrees() {
        // The old loader took the maximum here, which turns a mis-recorded point into a wider hit
        // area rather than into a report. A radius of 3 and a stray point at 9 would have been
        // answered with 9, and the ring would have scored a player who flew nowhere near it.
        Vec3 center = new Vec3(-12, 44, -300);
        List<Vec3> rim = List.of(
                center.plus(TILTED_FIRST),
                center.plus(TILTED_SECOND),
                center.plus(TILTED_FIRST.scale(3)));

        assertThatThrownBy(() -> RingGeometry.radius(center, rim))
                .isInstanceOf(InvalidCourseException.class)
                .hasMessageContaining("not all the same distance");
    }

    @Test
    void radiusRejectsARimSittingOnTheCentre() {
        Vec3 center = new Vec3(1, 2, 3);

        assertThatThrownBy(() -> RingGeometry.radius(center, List.of(center, center)))
                .isInstanceOf(InvalidCourseException.class)
                .hasMessageContaining("no radius");
    }

    @Test
    void radiusRejectsAnEmptyRim() {
        assertThatThrownBy(() -> RingGeometry.radius(new Vec3(0, 64, 0), List.of()))
                .isInstanceOf(InvalidCourseException.class)
                .hasMessageContaining("at least one rim point");
    }

    private static List<Vec3> rim(Vec3 center, int[][] offsets) {
        return Arrays.stream(offsets)
                .map(offset -> center.plus(new Vec3(offset[0], offset[1], offset[2])))
                .toList();
    }
}
