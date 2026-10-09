package net.elytrarace.voyager.setup.mapsetup;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.setup.mapsetup.exception.InvalidPoseException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

/**
 * Turns a builder's pose into a disc ring: the eye is the centre, the look direction is the normal, and the default
 * radius, points and type apply. The look direction is normalised here, because a client does not send a unit vector.
 */
@ApiStatus.Internal
public abstract class RingFromPose {

    private RingFromPose() {
    }

    /**
     * @param eye   the builder's eye position, which becomes the ring's centre
     * @param look  the builder's look direction; any non-zero length
     * @param index the index the ring takes, which is the number of rings before it
     * @return the ring with a unit normal along {@code look}
     * @throws InvalidPoseException if {@code look} has zero length or a length that is not finite
     */
    @Contract(pure = true, value = "_, _, _ -> new")
    public static Ring create(Vec3 eye, Vec3 look, int index) {
        double length = look.length();
        if (!Double.isFinite(length) || length == 0.0) {
            throw InvalidPoseException.degenerateLook(look);
        }
        Vec3 normal = look.scale(1.0 / length);
        return new Ring(index, eye, normal, RingDefaults.RADIUS, RingDefaults.POINTS, RingDefaults.TYPE);
    }
}
