package net.elytrarace.voyager.setup.mapsetup;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.Ring;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

import java.util.List;
import java.util.OptionalInt;

/**
 * Finds the ring a builder's look ray crosses: the nearest disc the ray hits within reach, rim inclusive.
 */
@ApiStatus.Internal
public abstract class RingPicker {

    /** A ray whose dot product with the normal is smaller than this is parallel to the disc. */
    private static final double PARALLEL_TOLERANCE = 1e-9;

    private RingPicker() {
    }

    /**
     * @param rings  the rings of the draft
     * @param origin the eye position the ray starts from
     * @param look   the look direction; normalised here, so any non-zero length is accepted
     * @param reach  the farthest distance along the ray, in blocks, that a ring may be hit at
     * @return the index of the nearest ring the ray crosses, or empty if it crosses none
     */
    @Contract(pure = true, value = "_, _, _, _ -> new")
    public static OptionalInt nearestCrossed(List<Ring> rings, Vec3 origin, Vec3 look, double reach) {
        double length = look.length();
        if (!Double.isFinite(length) || length == 0.0) {
            return OptionalInt.empty();
        }
        Vec3 direction = look.scale(1.0 / length);

        OptionalInt nearest = OptionalInt.empty();
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (Ring ring : rings) {
            double facing = ring.normal().dot(direction);
            if (Math.abs(facing) < PARALLEL_TOLERANCE) {
                continue;
            }
            double distance = ring.normal().dot(ring.center().minus(origin)) / facing;
            if (!(distance > 0.0 && distance <= reach)) {
                continue;
            }
            Vec3 hit = origin.plus(direction.scale(distance));
            if (hit.distanceTo(ring.center()) > ring.radius()) {
                continue;
            }
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = OptionalInt.of(ring.index());
            }
        }
        return nearest;
    }
}
