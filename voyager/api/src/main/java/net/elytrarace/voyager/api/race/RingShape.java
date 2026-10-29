package net.elytrarace.voyager.api.race;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.exception.InvalidRingException;

/**
 * The shape of a ring's area: a circle, or an oval whose two radii differ.
 *
 * <p>Every radius of a ring lies in {@code [MIN_RADIUS, MAX_RADIUS]} blocks, both ends inclusive. An oval whose two
 * radii are equal is the circle of that radius, so {@link #of} is the one way to make an oval from loose values: it
 * returns the {@link Circle} in that case, and each shape has exactly one representation.
 */
public sealed interface RingShape permits RingShape.Circle, RingShape.Ellipse {

    /** The smallest radius a ring may have, in blocks. */
    double MIN_RADIUS = 0.5;

    /** The largest radius a ring may have, in blocks. */
    double MAX_RADIUS = 32.0;

    /**
     * The radius a ring reports as its own: the radius of a circle, and the semi-major radius of an oval. It is the
     * outer bound of the shape, so a computer racer keeps its clearance from it.
     *
     * @return the radius, in blocks
     */
    double radius();

    /**
     * Makes the shape of an oval with the given radii, validating it against the ring's normal.
     *
     * @param radius      the semi-major radius, in blocks
     * @param radiusMinor the semi-minor radius, in blocks
     * @param majorAxis   the unit major axis, which must lie in the ring's plane
     * @param normal      the ring's unit normal
     * @return a {@link Circle} of {@code radius} when the two radii are equal, otherwise an {@link Ellipse}
     * @throws InvalidRingException if a radius is outside the bounds, the minor radius exceeds the major one, or the
     *                              axis is not of unit length or not perpendicular to the normal
     */
    static RingShape of(double radius, double radiusMinor, Vec3 majorAxis, Vec3 normal) {
        Ellipse ellipse = new Ellipse(radius, radiusMinor, majorAxis);
        ellipse.requirePerpendicularTo(normal);
        return radiusMinor == radius ? new Circle(radius) : ellipse;
    }

    /**
     * A circular disc: every point at most {@code radius} from the centre is inside.
     *
     * @param radius the radius, in blocks, within {@code [MIN_RADIUS, MAX_RADIUS]}
     */
    record Circle(double radius) implements RingShape {

        public Circle {
            requireRadius("radius", radius);
        }
    }

    /**
     * An elliptical area with a semi-major radius, a semi-minor radius and an in-plane unit major axis.
     *
     * <p>Create an oval through {@link RingShape#of}, which returns a {@link Circle} for equal radii. An {@code Ellipse}
     * built directly with equal radii describes the same area as that circle.
     *
     * @param radius      the semi-major radius, in blocks; the outer bound of the oval
     * @param radiusMinor the semi-minor radius, in blocks, not greater than {@code radius}
     * @param majorAxis   the unit vector of the major axis, lying in the ring's plane
     */
    record Ellipse(double radius, double radiusMinor, Vec3 majorAxis) implements RingShape {

        private static final double UNIT_LENGTH_TOLERANCE = 1e-9;
        private static final double PERPENDICULAR_TOLERANCE = 1e-6;

        public Ellipse {
            requireRadius("radius", radius);
            requireRadius("radiusMinor", radiusMinor);
            if (radiusMinor > radius) {
                throw InvalidRingException.minorRadiusAboveMajor(radiusMinor, radius);
            }
            if (Math.abs(majorAxis.length() - 1.0) > UNIT_LENGTH_TOLERANCE) {
                throw InvalidRingException.majorAxisNotUnitLength(majorAxis);
            }
        }

        /**
         * Refuses an axis that is not perpendicular to the ring's normal, which only the ring can see.
         *
         * @param normal the ring's unit normal
         * @return this oval
         * @throws InvalidRingException if the axis is not perpendicular to {@code normal} within 1e-6
         */
        public Ellipse requirePerpendicularTo(Vec3 normal) {
            if (Math.abs(majorAxis.dot(normal)) > PERPENDICULAR_TOLERANCE) {
                throw InvalidRingException.majorAxisNotPerpendicular(majorAxis, normal);
            }
            return this;
        }
    }

    /**
     * Refuses a radius that is not a number within the bounds. The negated comparison also refuses NaN.
     *
     * @param field the name of the radius in the file and the messages, such as {@code radiusMinor}
     * @param value the radius, in blocks
     */
    private static void requireRadius(String field, double value) {
        if (!(value >= MIN_RADIUS && value <= MAX_RADIUS)) {
            throw InvalidRingException.radiusOutOfRange(field, value);
        }
    }
}
