package net.elytrarace.voyager.race.line;

import net.elytrarace.voyager.api.math.Vec3;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.List;

/**
 * The curve through a list of points, sampled at roughly even spacing.
 *
 * <p>A <strong>centripetal</strong> Catmull-Rom spline, {@code alpha = 0.5}. The parameterisation is
 * the whole of the decision and it is not a matter of taste. Uniform Catmull-Rom ({@code alpha = 0})
 * overshoots at a control point whose neighbours are unevenly spaced, and overshoots into a cusp or a
 * self-intersection when they are very unevenly spaced (Yuksel, Schaefer, Keyser 2011). On a
 * racetrack that is not an aesthetic defect: a control point is placed precisely where a straight
 * line would cut through terrain, so it is always the tight one, and a line that bulges past it is a
 * line that loops through the wall the point was placed to avoid. Measured on this repository's own
 * hairpin fixture, the bulge past a 1.4-block control step is 0.21 blocks centripetal and 4.62 blocks
 * uniform.
 *
 * <p>This class knows nothing about rings, guides or maps — it takes points and returns points, which
 * is what makes the interesting assertions ("a straight line stays straight", "a bend does not
 * overshoot", "reversing the points mirrors the curve") writable without a course.
 *
 * <h2>Ported from {@code legacy/shared/spline}, with two changes</h2>
 *
 * <p>The tree being replaced has the same algorithm in {@code SplineGenerator}. It is good and this
 * is a port rather than a rewrite, but it takes {@code org.apache.commons.geometry}'s
 * {@code Vector3D}, and the rebuild is not adding a geometry library for a type it already has in
 * {@link Vec3}. And its sampler emits every segment's endpoints, so the point where two segments meet
 * appears twice in the output — a doubled particle at every ring, and a curve whose sample list is
 * not the same forwards as backwards. Here each segment contributes its interior samples plus its
 * end control point, so every control point appears exactly once and at its exact position rather
 * than at whatever the curve evaluates to there.
 */
@ApiStatus.Internal
public abstract class CatmullRom {

    /**
     * The centripetal exponent. Not configurable: {@code 0} is uniform and cusps, {@code 1} is
     * chordal and pulls the curve wide on long segments, and a racing line wants neither.
     */
    public static final double ALPHA = 0.5;

    /**
     * How many straight steps one segment's length is estimated over.
     *
     * <p>Sixteen, and it only decides how many particles a segment gets rather than where any of them
     * goes: the estimate is a lower bound that converges from below, and at sixteen steps it is
     * within a fraction of a percent of the true arc length on segments the size a racecourse has.
     * Spending 64 steps on it, as the tree being replaced did, buys a spacing accurate to a number of
     * decimal places nobody can see at 40 blocks a second.
     */
    private static final int LENGTH_ESTIMATE_STEPS = 16;

    /** Two control points closer than this share a knot, which the parameterisation cannot divide by. */
    private static final double MINIMUM_KNOT_SEPARATION = 1e-10;

    private CatmullRom() {
    }

    /**
     * Samples the curve through {@code controlPoints} at approximately {@code spacing} blocks.
     *
     * <p>Every control point is in the result, at its exact position, and
     * {@link Curve#controlPointIndices()} says where — that is what lets a caller find the stretch of
     * curve between two of them without searching for a position by value.
     *
     * @param controlPoints the points the curve must pass through, in order
     * @param spacing the target distance between two samples, in blocks; a segment shorter than this
     *     is drawn as its own chord
     * @return the sampled curve
     */
    @Contract(pure = true)
    public static Curve sample(List<Vec3> controlPoints, double spacing) {
        if (controlPoints.size() < 2) {
            List<Integer> indices = controlPoints.isEmpty() ? List.of() : List.of(0);
            return new Curve(List.copyOf(controlPoints), indices);
        }

        List<Vec3> extended = withPhantomEnds(controlPoints);
        List<Vec3> points = new ArrayList<>();
        List<Integer> controlPointIndices = new ArrayList<>(controlPoints.size());

        points.add(controlPoints.getFirst());
        controlPointIndices.add(0);
        for (int segment = 0; segment < controlPoints.size() - 1; segment++) {
            Vec3 p0 = extended.get(segment);
            Vec3 p1 = extended.get(segment + 1);
            Vec3 p2 = extended.get(segment + 2);
            Vec3 p3 = extended.get(segment + 3);
            double[] knots = knots(p0, p1, p2, p3);

            int steps = Math.max(1, (int) Math.round(length(p0, p1, p2, p3, knots) / spacing));
            for (int step = 1; step < steps; step++) {
                points.add(evaluate(p0, p1, p2, p3, knots, (double) step / steps));
            }
            // The segment's own end control point rather than evaluate(1.0). The two agree to within
            // rounding, and the difference is the whole reason to prefer this one: a ring's centre in
            // the sampled line is then the ring's centre, not a value that drifted a billionth of a
            // block on the way through a Barry-Goldman pyramid.
            points.add(controlPoints.get(segment + 1));
            controlPointIndices.add(points.size() - 1);
        }
        return new Curve(List.copyOf(points), List.copyOf(controlPointIndices));
    }

    /**
     * A sampled curve, and where in it each control point landed.
     *
     * @param points the curve, in order, starting at the first control point and ending at the last
     * @param controlPointIndices index into {@code points} of each control point, in the same order
     *     the control points were given
     */
    public record Curve(List<Vec3> points, List<Integer> controlPointIndices) {

        public Curve {
            points = List.copyOf(points);
            controlPointIndices = List.copyOf(controlPointIndices);
        }
    }

    /**
     * The control points with one mirrored point added at each end.
     *
     * <p>A Catmull-Rom segment needs a point on either side of it, and the first and last segment
     * have none. Mirroring the neighbour across the endpoint — {@code p0 + (p0 - p1)} — makes the
     * curve leave the first point along the direction of the first segment, which is the behaviour a
     * racer sees as "the line starts pointing where the course goes". The alternative, repeating the
     * endpoint, flattens the tangent to zero and puts a visible kink in the first stretch.
     */
    private static List<Vec3> withPhantomEnds(List<Vec3> controlPoints) {
        Vec3 first = controlPoints.getFirst();
        Vec3 last = controlPoints.getLast();
        List<Vec3> extended = new ArrayList<>(controlPoints.size() + 2);
        extended.add(first.plus(first.minus(controlPoints.get(1))));
        extended.addAll(controlPoints);
        extended.add(last.plus(last.minus(controlPoints.get(controlPoints.size() - 2))));
        return extended;
    }

    /**
     * The four knot values, which is where {@link #ALPHA} enters and the only place it does.
     *
     * <p>Each knot advances by the distance to the next point raised to alpha, so a long segment gets
     * a long parameter interval and the curve does not have to hurry through it. With alpha zero
     * every interval is one regardless of distance, which is the uniform parameterisation and the
     * cusp.
     */
    private static double[] knots(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3) {
        double t0 = 0.0;
        double t1 = t0 + Math.pow(Math.max(p0.distanceTo(p1), MINIMUM_KNOT_SEPARATION), ALPHA);
        double t2 = t1 + Math.pow(Math.max(p1.distanceTo(p2), MINIMUM_KNOT_SEPARATION), ALPHA);
        double t3 = t2 + Math.pow(Math.max(p2.distanceTo(p3), MINIMUM_KNOT_SEPARATION), ALPHA);
        return new double[] {t0, t1, t2, t3};
    }

    /**
     * One point on the segment between {@code p1} and {@code p2}, by the Barry-Goldman pyramid.
     *
     * @param localT {@code 0} at {@code p1}, {@code 1} at {@code p2}
     */
    private static Vec3 evaluate(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double[] knots, double localT) {
        double t0 = knots[0];
        double t1 = knots[1];
        double t2 = knots[2];
        double t3 = knots[3];
        double t = t1 + localT * (t2 - t1);

        double d10 = Math.max(t1 - t0, MINIMUM_KNOT_SEPARATION);
        double d21 = Math.max(t2 - t1, MINIMUM_KNOT_SEPARATION);
        double d32 = Math.max(t3 - t2, MINIMUM_KNOT_SEPARATION);
        double d20 = Math.max(t2 - t0, MINIMUM_KNOT_SEPARATION);
        double d31 = Math.max(t3 - t1, MINIMUM_KNOT_SEPARATION);

        Vec3 a1 = p0.scale((t1 - t) / d10).plus(p1.scale((t - t0) / d10));
        Vec3 a2 = p1.scale((t2 - t) / d21).plus(p2.scale((t - t1) / d21));
        Vec3 a3 = p2.scale((t3 - t) / d32).plus(p3.scale((t - t2) / d32));

        Vec3 b1 = a1.scale((t2 - t) / d20).plus(a2.scale((t - t0) / d20));
        Vec3 b2 = a2.scale((t3 - t) / d31).plus(a3.scale((t - t1) / d31));

        return b1.scale((t2 - t) / d21).plus(b2.scale((t - t1) / d21));
    }

    /** The segment's arc length, estimated as the polyline through {@link #LENGTH_ESTIMATE_STEPS} steps. */
    private static double length(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double[] knots) {
        double length = 0.0;
        Vec3 previous = p1;
        for (int step = 1; step <= LENGTH_ESTIMATE_STEPS; step++) {
            Vec3 current = evaluate(p0, p1, p2, p3, knots, (double) step / LENGTH_ESTIMATE_STEPS);
            length += previous.distanceTo(current);
            previous = current;
        }
        return length;
    }
}
