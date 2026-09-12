package net.elytrarace.tools.converter;

import net.elytrarace.tools.converter.exception.InvalidCourseException;
import net.elytrarace.voyager.api.math.Vec3;

import java.util.List;

/**
 * The plane a ring lies in and how wide it is, read off the rim points the setup wizard recorded.
 *
 * <p>The old format stores a bag of block coordinates and nothing else — no normal, no radius — so
 * both have to come from the rim. This class derives the <em>axis</em> only: a unit vector
 * perpendicular to the ring's plane, with an arbitrary sign. Which of the two directions along that
 * axis counts as "through the ring" is a different question, answered by {@link CourseConverter}
 * from the shape of the flight path rather than from the ring alone.
 *
 * <p><strong>The degenerate-pair trap.</strong> The obvious derivation — cross the first two rim
 * vectors — fails on this data, and fails quietly. The rim points are written in order around the
 * ring, and with eight of them the first and second entry of some rings are diametrically opposite:
 * their cross product is the zero vector, which normalises to NaN or, if the code guards for it,
 * falls back to a hard-coded default. The loader in the tree being replaced did exactly that and
 * shipped {@code (0, 0, 1)} for those rings. So the search here runs over <em>pairs</em> until it
 * finds one that spans the plane, and fails loudly if no pair does.
 */
public final class RingGeometry {

    /**
     * How far a rim point may sit out of the derived plane, in blocks. The coordinates are integers,
     * so a genuinely planar ring lands on exactly zero; this tolerance only absorbs the rounding of
     * the cross product and the normalisation, never a real deviation.
     */
    static final double PLANARITY_TOLERANCE = 1e-9;

    /**
     * How far two rim points' distances from the centre may differ, in blocks. Every ring in the
     * repository is a regular polygon — all 35 rings of {@code ElytraraceBlueAndRed} measure exactly
     * {@code sqrt(13)} on every rim point — so a spread wider than rounding means the recorded
     * points are not one ring, and picking the largest of them (which the old loader did) would
     * invent a hit area the builder never drew.
     */
    static final double RADIUS_TOLERANCE = 1e-9;

    /** A cross product shorter than this spans no plane and cannot be normalised into an axis. */
    private static final double DEGENERATE_CROSS_PRODUCT = 1e-9;

    private RingGeometry() {
    }

    /**
     * The unit vector perpendicular to the plane the rim lies in, with an arbitrary sign.
     *
     * @param center the ring's centre, as recorded
     * @param rim    the ring's rim points, as recorded, in file order
     * @return a unit-length axis of the ring's plane; the opposite vector is an equally valid answer
     * @throws InvalidCourseException if there are fewer than two rim points, if no pair of them
     *                                spans a plane, or if some rim point lies off that plane
     */
    public static Vec3 axis(Vec3 center, List<Vec3> rim) {
        if (rim.size() < 2) {
            throw new InvalidCourseException(
                    "a ring needs at least two rim points to derive a normal from, got %s".formatted(rim.size()));
        }

        Vec3 axis = null;
        for (int i = 0; i < rim.size() && axis == null; i++) {
            for (int j = i + 1; j < rim.size(); j++) {
                Vec3 candidate = cross(rim.get(i).minus(center), rim.get(j).minus(center));
                if (candidate.length() > DEGENERATE_CROSS_PRODUCT) {
                    axis = candidate.scale(1.0 / candidate.length());
                    break;
                }
            }
        }
        if (axis == null) {
            throw new InvalidCourseException(
                    ("no two of the %s rim points around %s span a plane; every pair is collinear with "
                            + "the centre, so the ring has no normal").formatted(rim.size(), center));
        }

        for (Vec3 point : rim) {
            double offPlane = Math.abs(point.minus(center).dot(axis));
            if (offPlane > PLANARITY_TOLERANCE) {
                throw new InvalidCourseException(
                        ("rim point %s lies %s blocks off the plane through %s with normal %s; the "
                                + "recorded points are not one flat ring").formatted(point, offPlane, center, axis));
            }
        }
        return axis;
    }

    /**
     * How far the rim sits from the centre.
     *
     * @param center the ring's centre, as recorded
     * @param rim    the ring's rim points, as recorded
     * @return the shared distance from the centre to every rim point
     * @throws InvalidCourseException if the rim is empty, if it sits on the centre, or if the rim
     *                                points are not all the same distance away
     */
    public static double radius(Vec3 center, List<Vec3> rim) {
        if (rim.isEmpty()) {
            throw new InvalidCourseException("a ring needs at least one rim point to derive a radius from");
        }
        double radius = rim.getFirst().distanceTo(center);
        for (Vec3 point : rim) {
            double distance = point.distanceTo(center);
            if (Math.abs(distance - radius) > RADIUS_TOLERANCE) {
                throw new InvalidCourseException(
                        ("rim points around %s are not all the same distance away: %s is %s blocks out, "
                                + "%s is %s").formatted(center, rim.getFirst(), radius, point, distance));
            }
        }
        if (radius <= 0.0) {
            throw new InvalidCourseException(
                    "every rim point sits on the centre %s, so the ring has no radius".formatted(center));
        }
        return radius;
    }

    /**
     * The cross product, which {@link Vec3} does not carry.
     *
     * <p>It is not added there on purpose: this tool is the only caller in the repository, and the
     * rebuild's convention is that a vector operation arrives in the same commit as the code that
     * needs it rather than because a vector type is expected to have one.
     */
    private static Vec3 cross(Vec3 first, Vec3 second) {
        return new Vec3(
                first.y() * second.z() - first.z() * second.y(),
                first.z() * second.x() - first.x() * second.z(),
                first.x() * second.y() - first.y() * second.x());
    }
}
