package net.elytrarace.voyager.race.line;

import net.elytrarace.voyager.api.math.Vec3;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CatmullRomTest {

    private static final Offset<Double> ROUNDING = Offset.offset(1e-9);

    /**
     * Four points 10 blocks apart along {@code z}, at {@code y = 64} — so a curve that wandered off
     * the line has to wander in a coordinate that is not zero, where a dropped term would be
     * invisible.
     */
    private static final List<Vec3> STRAIGHT = List.of(
            new Vec3(0.0, 64.0, 0.0), new Vec3(0.0, 64.0, 10.0),
            new Vec3(0.0, 64.0, 20.0), new Vec3(0.0, 64.0, 30.0));

    /**
     * A hairpin: 40 blocks out, a 1.4-block step across the top, and 41 blocks back almost the way it
     * came. This is the shape a guide point placed to steer a line around an obstacle actually makes,
     * and the shape uniform Catmull-Rom cannot draw — see {@link #doesNotOvershootAtATightControlPoint}.
     */
    private static final List<Vec3> HAIRPIN = List.of(
            new Vec3(0.0, 64.0, 0.0), new Vec3(40.0, 64.0, 0.0),
            new Vec3(41.0, 64.0, 1.0), new Vec3(0.0, 64.0, 2.0));

    /** Fine enough that even the 1.4-block middle segment of the hairpin gets interior samples. */
    private static final double FINE = 0.1;

    @Test
    void drawsCollinearPointsAsAStraightLine() {
        List<Vec3> points = CatmullRom.sample(STRAIGHT, 1.0).points();

        for (Vec3 point : points) {
            assertThat(point.x()).as("x of %s", point).isCloseTo(0.0, ROUNDING);
            assertThat(point.y()).as("y of %s", point).isCloseTo(64.0, ROUNDING);
        }
        assertThat(points.getFirst()).isEqualTo(STRAIGHT.getFirst());
        assertThat(points.getLast()).isEqualTo(STRAIGHT.getLast());
        assertThat(length(points)).isCloseTo(30.0, ROUNDING);
    }

    /**
     * The one assertion that separates a centripetal spline from a uniform one, and the reason this
     * class exists rather than a {@code Vec3} lerp.
     *
     * <p>Between the two points at the top of the hairpin — 1.4 blocks apart, with 40-block segments
     * on either side — the centripetal curve bulges 0.21 blocks off the chord. The same points with
     * {@code alpha = 0} bulge 4.62, which on a racetrack is the line leaving the gap the guide point
     * was placed in. Half a block is well clear of one and nowhere near the other.
     */
    @Test
    void doesNotOvershootAtATightControlPoint() {
        CatmullRom.Curve curve = CatmullRom.sample(HAIRPIN, FINE);

        List<Vec3> acrossTheTop = curve.points().subList(
                curve.controlPointIndices().get(1), curve.controlPointIndices().get(2) + 1);

        assertThat(farthestFromTheChord(acrossTheTop, HAIRPIN.get(1), HAIRPIN.get(2)))
                .as("uniform Catmull-Rom bulges 4.62 blocks here; centripetal bulges 0.21")
                .isLessThan(0.5);
    }

    /**
     * The curve is the same shape flown backwards.
     *
     * <p>Not a symmetry for its own sake: the sampler distributes points per segment, and the obvious
     * way to write that — samples at {@code j/n} for {@code j} in {@code 0..n-1}, which is what the
     * tree being replaced does — is off by half a step in one direction and produces a doubled point
     * at every join. This is the assertion that catches it, because neither shows up in a picture.
     */
    @Test
    void reversingTheControlPointsMirrorsTheCurve() {
        List<Vec3> forwards = CatmullRom.sample(HAIRPIN, FINE).points();
        List<Vec3> backwards = CatmullRom.sample(HAIRPIN.reversed(), FINE).points();

        assertThat(backwards).hasSameSizeAs(forwards);
        List<Vec3> mirrored = forwards.reversed();
        for (int i = 0; i < mirrored.size(); i++) {
            assertThat(backwards.get(i).distanceTo(mirrored.get(i)))
                    .as("point %s of %s", i, mirrored.size())
                    .isCloseTo(0.0, Offset.offset(1e-9));
        }
    }

    /**
     * Every control point is in the curve exactly once, at exactly its own position, and the reported
     * index is where.
     *
     * <p>Exactly its own position rather than close to it: a ring's centre in the sampled line has to
     * be the ring's centre, so that a stretch selected by ring index starts where the ring is and not
     * a billionth of a block from it.
     */
    @Test
    void putsEveryControlPointInTheCurveAtItsOwnPosition() {
        CatmullRom.Curve curve = CatmullRom.sample(HAIRPIN, FINE);

        assertThat(curve.controlPointIndices()).hasSameSizeAs(HAIRPIN);
        for (int i = 0; i < HAIRPIN.size(); i++) {
            assertThat(curve.points().get(curve.controlPointIndices().get(i)))
                    .as("control point %s", i)
                    .isEqualTo(HAIRPIN.get(i));
        }
        assertThat(curve.controlPointIndices()).isSorted();
        assertThat(curve.controlPointIndices().getFirst()).isZero();
        assertThat(curve.controlPointIndices().getLast()).isEqualTo(curve.points().size() - 1);
    }

    /**
     * The spacing decides how many points a segment gets, and the control points stay put regardless.
     *
     * <p>Three 10-block segments: 10 samples each at a block apart, 3 each at three blocks apart, one
     * each when the spacing is wider than the segment. That last case is the one worth pinning — a
     * short segment becomes its own chord rather than disappearing or producing a division by zero.
     */
    @Test
    void spacingDecidesHowManyPointsASegmentGets() {
        assertThat(CatmullRom.sample(STRAIGHT, 1.0).points()).hasSize(31);
        assertThat(CatmullRom.sample(STRAIGHT, 1.0).controlPointIndices()).containsExactly(0, 10, 20, 30);

        assertThat(CatmullRom.sample(STRAIGHT, 3.0).points()).hasSize(10);
        assertThat(CatmullRom.sample(STRAIGHT, 3.0).controlPointIndices()).containsExactly(0, 3, 6, 9);

        assertThat(CatmullRom.sample(STRAIGHT, 40.0).points()).hasSize(4);
        assertThat(CatmullRom.sample(STRAIGHT, 40.0).controlPointIndices()).containsExactly(0, 1, 2, 3);
    }

    @Test
    void returnsFewerThanTwoPointsUntouched() {
        // A one-ring course has no segment to sample. It is still a line of one point rather than an
        // error, because the caller that would have to handle the error is a renderer, which has
        // nothing to draw either way.
        Vec3 only = new Vec3(1.0, 64.0, 2.0);

        CatmullRom.Curve curve = CatmullRom.sample(List.of(only), 1.0);

        assertThat(curve.points()).containsExactly(only);
        assertThat(curve.controlPointIndices()).containsExactly(0);
        assertThat(CatmullRom.sample(List.of(), 1.0).points()).isEmpty();
    }

    private static double length(List<Vec3> points) {
        double length = 0.0;
        for (int i = 1; i < points.size(); i++) {
            length += points.get(i - 1).distanceTo(points.get(i));
        }
        return length;
    }

    private static double farthestFromTheChord(List<Vec3> points, Vec3 from, Vec3 to) {
        Vec3 chord = to.minus(from);
        double lengthSquared = chord.lengthSquared();
        List<Double> distances = new ArrayList<>(points.size());
        for (Vec3 point : points) {
            double along = Math.clamp(point.minus(from).dot(chord) / lengthSquared, 0.0, 1.0);
            distances.add(point.distanceTo(from.plus(chord.scale(along))));
        }
        return distances.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
    }
}
