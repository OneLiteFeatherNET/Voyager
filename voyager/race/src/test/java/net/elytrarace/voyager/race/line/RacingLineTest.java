package net.elytrarace.voyager.race.line;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.GuidePoint;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The line through a course.
 *
 * <p>The fixture is a five-ring course that climbs and turns, with one guide point in the gap between
 * rings 1 and 2 and <em>two</em> in the gap between rings 2 and 3 — the doubled gap being the case
 * the committed {@code ElytraraceBlueAndRed} has (order indices 2450 and 2475, both between rings 24
 * and 25) and the case an implementation that kept one guide per gap loses silently.
 */
class RacingLineTest {

    private static final List<Ring> RINGS = List.of(
            ring(0, new Vec3(0.0, 64.0, 0.0)),
            ring(1, new Vec3(0.0, 70.0, 60.0)),
            ring(2, new Vec3(40.0, 80.0, 110.0)),
            ring(3, new Vec3(40.0, 90.0, 170.0)),
            ring(4, new Vec3(0.0, 100.0, 220.0)));

    private static final GuidePoint BEFORE_THE_TURN = new GuidePoint(150, new Vec3(20.0, 76.0, 80.0));

    private static final GuidePoint AROUND_THE_OUTSIDE = new GuidePoint(250, new Vec3(60.0, 86.0, 130.0));

    private static final GuidePoint BACK_ON_LINE = new GuidePoint(275, new Vec3(55.0, 88.0, 150.0));

    /**
     * The straight-line length of the course, ring centre to ring centre: 250.74 blocks. Everything
     * below that talks about how long the line is is a ratio against this.
     */
    private static final double RING_TO_RING_LENGTH = straightThroughTheRings();

    @Test
    void runsThroughEveryRingCentreInOrder() {
        RacingLine line = RacingLine.of(courseWith(BEFORE_THE_TURN, AROUND_THE_OUTSIDE, BACK_ON_LINE));

        assertThat(line.ringCount()).isEqualTo(5);
        for (int ring = 0; ring < RINGS.size(); ring++) {
            assertThat(line.points().get(line.ringPoints().get(ring)))
                    .as("ring %s of the line", ring)
                    .isEqualTo(RINGS.get(ring).center());
        }
        assertThat(line.points().getFirst()).isEqualTo(RINGS.getFirst().center());
        assertThat(line.points().getLast()).isEqualTo(RINGS.getLast().center());
    }

    /**
     * Both guides of the doubled gap are on the line, and in order index order — 250 before 275.
     *
     * <p>{@code containsSubsequence} rather than {@code contains}: an implementation that reached the
     * later guide first and doubled back would still contain both points, and the whole failure this
     * pins is an order, not a membership.
     */
    @Test
    void bendsThroughEveryGuidePointInOrderIndexOrder() {
        RacingLine line = RacingLine.of(courseWith(BEFORE_THE_TURN, AROUND_THE_OUTSIDE, BACK_ON_LINE));

        assertThat(line.points()).containsSubsequence(
                RINGS.get(1).center(),
                BEFORE_THE_TURN.position(),
                RINGS.get(2).center(),
                AROUND_THE_OUTSIDE.position(),
                BACK_ON_LINE.position(),
                RINGS.get(3).center());
    }

    @Test
    void drawsACourseWithNoGuidesAsTheLineThroughItsRingsAlone() {
        RacingLine line = RacingLine.of(courseWith());

        assertThat(line.ringCount()).isEqualTo(5);
        assertThat(line.points()).doesNotContain(
                BEFORE_THE_TURN.position(), AROUND_THE_OUTSIDE.position(), BACK_ON_LINE.position());
        // 1.01: a curve through five rings is barely longer than the straight line through them. The
        // guides above cost another five per cent, and a guide in the wrong gap costs seventy-five.
        assertThat(lengthOf(line) / RING_TO_RING_LENGTH).isCloseTo(1.01, org.assertj.core.data.Offset.offset(0.01));
    }

    /**
     * A guide bends the line without materially lengthening it — and that is the measurement a wrong
     * interleaving cannot survive.
     *
     * <p>All sixteen guides of the committed course were measured against the two rings that bracket
     * them and every one lengthens its gap by a factor between 1.00 and 1.20, which is what a control
     * point placed to steer around terrain does. The same band holds for the whole line here. Move one
     * guide into the gap it does not belong in — the same point, the same course, one different order
     * index — and the line goes out and comes back: 1.75, nowhere near the band. That is why this is
     * the assertion worth keeping rather than a count of points, which a misplaced guide would pass.
     */
    @Test
    void aGuideBendsTheLineWithoutMateriallyLengtheningIt() {
        RacingLine correct = RacingLine.of(courseWith(BEFORE_THE_TURN, AROUND_THE_OUTSIDE, BACK_ON_LINE));

        assertThat(lengthOf(correct) / RING_TO_RING_LENGTH).isBetween(1.00, 1.20);

        GuidePoint misplaced = new GuidePoint(50, AROUND_THE_OUTSIDE.position());
        RacingLine wrongGap = RacingLine.of(courseWith(BEFORE_THE_TURN, misplaced, BACK_ON_LINE));

        assertThat(lengthOf(wrongGap) / RING_TO_RING_LENGTH)
                .as("a guide read into the wrong gap sends the line across the course and back")
                .isGreaterThan(1.5);
    }

    /**
     * A stretch runs from one ring's own point to another's, both included.
     *
     * <p>The two gaps are deliberately different lengths and only one of them holds guides, so a
     * stretch that started or ended at the wrong ring is a different number of points and a different
     * pair of ends.
     */
    @Test
    void aStretchRunsBetweenTwoRingsInclusive() {
        RacingLine line = RacingLine.of(courseWith(BEFORE_THE_TURN, AROUND_THE_OUTSIDE, BACK_ON_LINE));

        List<Vec3> oneGap = line.between(1, 2);
        assertThat(oneGap.getFirst()).isEqualTo(RINGS.get(1).center());
        assertThat(oneGap.getLast()).isEqualTo(RINGS.get(2).center());
        assertThat(oneGap).contains(BEFORE_THE_TURN.position());
        assertThat(oneGap).doesNotContain(AROUND_THE_OUTSIDE.position());

        List<Vec3> twoGaps = line.between(1, 3);
        assertThat(twoGaps).hasSizeGreaterThan(oneGap.size());
        assertThat(twoGaps.getLast()).isEqualTo(RINGS.get(3).center());

        List<Vec3> wholeLine = line.between(0, 4);
        assertThat(wholeLine).isEqualTo(line.points());

        assertThat(line.between(2, 2)).containsExactly(RINGS.get(2).center());
    }

    /**
     * The spacing the map asks for is the spacing the line is sampled at.
     *
     * <p>Two blocks apart is about half the points of one block apart. Asserted as a ratio rather than
     * an exact count because each segment rounds its own sample count, and the assertion is that the
     * map's number is read at all — a line sampled at a constant would be identical in both.
     */
    @Test
    void samplesAtTheSpacingTheMapAsksFor() {
        int fine = RacingLine.of(courseWith(1.0, BEFORE_THE_TURN)).points().size();
        int coarse = RacingLine.of(courseWith(2.0, BEFORE_THE_TURN)).points().size();

        assertThat(coarse).isLessThan(fine);
        assertThat((double) fine / coarse).isBetween(1.8, 2.2);
    }

    private static MapDefinition courseWith(GuidePoint... guides) {
        return courseWith(1.0, guides);
    }

    private static MapDefinition courseWith(double particleSpacing, GuidePoint... guides) {
        return new MapDefinition("ridge-run", "ridge_arena", new Vec3(0.5, 64.0, -10.0), RINGS,
                Duration.ofSeconds(30), new BoostConfig(12, 25),
                new GuideLine(List.of(guides), 2, particleSpacing));
    }

    private static Ring ring(int index, Vec3 center) {
        return new Ring(index, center, new Vec3(0.0, 0.0, 1.0), 5.0, 10, RingType.STANDARD);
    }

    private static double lengthOf(RacingLine line) {
        double length = 0.0;
        for (int i = 1; i < line.points().size(); i++) {
            length += line.points().get(i - 1).distanceTo(line.points().get(i));
        }
        return length;
    }

    private static double straightThroughTheRings() {
        double length = 0.0;
        for (int i = 1; i < RINGS.size(); i++) {
            length += RINGS.get(i - 1).center().distanceTo(RINGS.get(i).center());
        }
        return length;
    }
}
