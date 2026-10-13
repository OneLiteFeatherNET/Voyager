package net.elytrarace.voyager.race.com;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.race.line.RacingLine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The arc-length path a computer racer follows.
 *
 * <p>The fixtures are built directly as {@link RacingLine}s, so the path is tested with no course, no map
 * and no server. The straight fixture is collinear but not axis-aligned, so a path that mixed up the
 * coordinates would show.
 */
class ComPathTest {

    /** Three collinear points: a segment of 5 blocks, then one of 15 blocks along the same direction. */
    private static final RacingLine STRAIGHT = new RacingLine(
            List.of(new Vec3(1.0, 64.0, 2.0), new Vec3(4.0, 64.0, 6.0), new Vec3(13.0, 64.0, 18.0)),
            List.of(0, 2));

    /** A course that turns twice: 10 blocks along z, 10 along x, then 20 along z. */
    private static final RacingLine BENT = new RacingLine(
            List.of(
                    new Vec3(0.0, 64.0, 0.0),
                    new Vec3(0.0, 64.0, 10.0),
                    new Vec3(10.0, 64.0, 10.0),
                    new Vec3(10.0, 64.0, 30.0)),
            List.of(0, 3));

    @Test
    void theArcLengthOfAStraightCourseIsTheSumOfItsSegments() {
        ComPath path = ComPath.of(STRAIGHT);

        assertThat(path.length())
                .as("the length of 5 blocks plus 15 blocks of straight course")
                .isCloseTo(5.0 + 15.0, within(1e-9));
    }

    @Test
    void aDistanceInsideASegmentIsThePointThatFarAlongIt() {
        ComPath path = ComPath.of(STRAIGHT);

        // 12.5 blocks is 7.5 blocks into the 15-block second segment, which is halfway along it.
        assertThat(path.pointAt(12.5))
                .as("the point halfway along the second segment")
                .isEqualTo(new Vec3(8.5, 64.0, 12.0));
    }

    @Test
    void aDistanceBeforeTheStartClampsToTheFirstPoint() {
        ComPath path = ComPath.of(STRAIGHT);

        assertThat(path.pointAt(-5.0))
                .as("a distance before the start")
                .isEqualTo(new Vec3(1.0, 64.0, 2.0));
    }

    @Test
    void aDistancePastTheEndClampsToTheLastPoint() {
        ComPath path = ComPath.of(STRAIGHT);

        assertThat(path.pointAt(path.length() + 100.0))
                .as("a distance past the end")
                .isEqualTo(new Vec3(13.0, 64.0, 18.0));
    }

    @Test
    void theTangentAtAnyDistanceHasUnitLength() {
        ComPath path = ComPath.of(BENT);

        for (double distance : new double[] {-1.0, 0.0, 4.0, 10.0, 15.0, 20.0, 35.0, 40.0, 60.0}) {
            assertThat(path.tangentAt(distance).length())
                    .as("the tangent length at distance %s along the bent course".formatted(distance))
                    .isCloseTo(1.0, within(1e-9));
        }
    }
}
