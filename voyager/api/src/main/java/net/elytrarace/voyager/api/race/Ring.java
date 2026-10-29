package net.elytrarace.voyager.api.race;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.exception.InvalidRingException;

/**
 * A ring a player scores by flying through. Its hit area is the area of its {@link RingShape}, in the plane through
 * the centre that the normal defines: a disc for a circle, an ellipse for an oval. The rim is inclusive.
 *
 * <p>The normal is stored rather than derived from two edge vectors of the ring's geometry — see the
 * greenfield design's decision table (D-E3-5) — so it must already be unit length by the time a
 * {@code Ring} is constructed.
 */
public record Ring(int index, Vec3 center, Vec3 normal, RingShape shape, int points, RingType type) {

    private static final double UNIT_LENGTH_TOLERANCE = 1e-9;
    private static final double PERPENDICULAR_TOLERANCE = 1e-6;
    private static final int MAX_POINTS = 100;

    public Ring {
        if (index < 0) {
            throw InvalidRingException.negativeIndex(index);
        }
        if (points < 0 || points > MAX_POINTS) {
            throw InvalidRingException.pointsOutOfRange(points);
        }
        if (Math.abs(normal.length() - 1.0) > UNIT_LENGTH_TOLERANCE) {
            throw InvalidRingException.normalNotUnitLength(normal);
        }
        if (shape instanceof RingShape.Ellipse ellipse) {
            ellipse.requirePerpendicularTo(normal);
        }
    }

    /**
     * A circular ring of the given radius, which is the ring every existing call site builds.
     *
     * @throws InvalidRingException if the radius, the points or the normal is outside what a ring may hold
     */
    public Ring(int index, Vec3 center, Vec3 normal, double radius, int points, RingType type) {
        this(index, center, normal, new RingShape.Circle(radius), points, type);
    }

    /**
     * The outer bound of the ring: the radius of a circle, and the semi-major radius of an oval. A computer racer keeps
     * its clearance from this bound, so the whole outline of an oval is cleared.
     *
     * @return the radius, in blocks
     */
    public double radius() {
        return shape.radius();
    }

    /**
     * Whether a point lies inside the ring's shape.
     *
     * <p>The point is expected in the ring's plane. For a circle the test is the distance from the centre, including
     * any offset out of the plane, which is the test the game has always used. For an oval the offset is taken within
     * the plane only, so a point off the plane is judged by where it projects onto the plane.
     *
     * @param point a point in the plane of the ring
     * @return whether the point is inside the shape, the rim included
     */
    public boolean contains(Vec3 point) {
        return switch (shape) {
            case RingShape.Circle circle -> point.distanceTo(center) <= circle.radius();
            case RingShape.Ellipse ellipse -> containsInEllipse(ellipse, point);
        };
    }

    private boolean containsInEllipse(RingShape.Ellipse ellipse, Vec3 point) {
        Vec3 offset = point.minus(center);
        double along = offset.dot(ellipse.majorAxis()) / ellipse.radius();
        double across = offset.dot(crossProduct(normal, ellipse.majorAxis())) / ellipse.radiusMinor();
        return along * along + across * across <= 1.0;
    }

    /** The in-plane unit vector perpendicular to the major axis, which is the minor axis of an oval. */
    private static Vec3 crossProduct(Vec3 a, Vec3 b) {
        return new Vec3(a.y() * b.z() - a.z() * b.y(), a.z() * b.x() - a.x() * b.z(), a.x() * b.y() - a.y() * b.x());
    }
}
