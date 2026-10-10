package net.elytrarace.server.physics;

import net.minestom.server.coordinate.Vec;

/**
 * Stateless utility class for detecting when a player passes through a ring checkpoint.
 * <p>
 * Uses a line-segment vs. plane intersection test followed by a radius check
 * to determine if the movement from {@code prevPos} to {@code currPos} passes through
 * the ring defined by its center, normal, and radius.
 */
public final class RingCollisionDetector {

    private static final double EPSILON = 1e-8;

    private RingCollisionDetector() {
        // utility class
    }

    /**
     * Checks whether the line segment from {@code prevPos} to {@code currPos} passes through
     * the ring defined by the given center, normal, and radius.
     *
     * @param ringCenter the center of the ring
     * @param ringNormal the normal vector of the ring plane (must be normalized)
     * @param ringRadius the radius of the ring
     * @param prevPos    the player's position at the start of the tick
     * @param currPos    the player's position at the end of the tick
     * @return {@code true} if the line segment passes through the ring
     */
    public static boolean checkPassthrough(Vec ringCenter, Vec ringNormal, double ringRadius,
                                           Vec prevPos, Vec currPos) {
        // Direction vector of the line segment
        Vec d = currPos.sub(prevPos);

        // Denominator: dot(normal, direction)
        double denom = ringNormal.dot(d);

        // If denom is ~0 the segment is parallel to the ring plane — no passthrough
        if (Math.abs(denom) < EPSILON) {
            return false;
        }

        // Compute parameter t for the intersection with the ring plane
        // Plane equation: dot(N, P - C) = 0
        // Line: P(t) = prevPos + t * d
        // => t = dot(N, C - prevPos) / dot(N, d)
        double t = ringNormal.dot(ringCenter.sub(prevPos)) / denom;

        // The intersection must lie within the segment [0, 1]
        if (t < 0.0 || t > 1.0) {
            return false;
        }

        // Compute the intersection point
        Vec intersection = prevPos.add(d.mul(t));

        // Check if the intersection point is within the ring radius
        double distToCenter = intersection.sub(ringCenter).length();

        return distToCenter <= ringRadius;
    }

    /**
     * Convenience overload using a {@link Ring} record.
     *
     * @param ring    the ring to test against
     * @param prevPos the player's position at the start of the tick
     * @param currPos the player's position at the end of the tick
     * @return {@code true} if the line segment passes through the ring
     */
    public static boolean checkPassthrough(Ring ring, Vec prevPos, Vec currPos) {
        return checkPassthrough(ring.center(), ring.normal(), ring.radius(), prevPos, currPos);
    }
}
