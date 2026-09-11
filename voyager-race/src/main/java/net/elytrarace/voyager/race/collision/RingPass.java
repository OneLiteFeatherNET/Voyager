package net.elytrarace.voyager.race.collision;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.Ring;

import org.jetbrains.annotations.ApiStatus;

/**
 * Whether a player passed through a {@link Ring} between two tick positions.
 *
 * <p>The hit area is a disc: the circumcircle of the ring's corner points, edge inclusive. The test
 * runs over the segment between the previous and current tick position; direction is not checked, so
 * a backwards pass through a ring counts, matching the behaviour players have today.
 */
@ApiStatus.Internal
public abstract class RingPass {

    /**
     * How close to zero the plane/segment dot product may be before the segment is treated as
     * parallel to the ring's plane, matching the tree this replaces.
     */
    private static final double PARALLEL_EPSILON = 1e-8;

    private RingPass() {
    }

    /**
     * Returns whether the segment from {@code from} to {@code to} crosses {@code ring}'s disc.
     *
     * <p>The radius comparison is exact: the rim is inclusive and no tolerance is added to it, since a
     * tolerance there would quietly enlarge every ring.
     */
    public static boolean crosses(Vec3 from, Vec3 to, Ring ring) {
        Vec3 step = to.minus(from);
        double denominator = ring.normal().dot(step);
        if (Math.abs(denominator) < PARALLEL_EPSILON) {
            return false;
        }
        double t = ring.normal().dot(ring.center().minus(from)) / denominator;
        if (t < 0.0 || t > 1.0) {
            return false;
        }
        Vec3 intersection = from.plus(step.scale(t));
        return intersection.distanceTo(ring.center()) <= ring.radius();
    }
}
