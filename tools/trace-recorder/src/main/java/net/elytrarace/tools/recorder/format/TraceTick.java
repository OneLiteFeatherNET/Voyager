package net.elytrarace.tools.recorder.format;

import net.elytrarace.tools.recorder.format.exception.InvalidTraceException;

/**
 * One tick of a recorded glide, sampled after the entity has been ticked.
 *
 * <p>Position and velocity are {@code double} and rotation is {@code float}, mirroring Vanilla's own
 * numeric types. Velocity is the entity's real internal delta movement, not a position difference.
 */
public record TraceTick(
        int index,
        double posX, double posY, double posZ,
        double velX, double velY, double velZ,
        float yaw, float pitch,
        boolean onGround,
        boolean fireworkBoostActive,
        int fireworkTicksRemaining) {

    public TraceTick {
        if (index < 0) {
            throw new InvalidTraceException("tick index must be >= 0, was %s".formatted(index));
        }
        if (!Double.isFinite(posX) || !Double.isFinite(posY) || !Double.isFinite(posZ)
                || !Double.isFinite(velX) || !Double.isFinite(velY) || !Double.isFinite(velZ)) {
            throw new InvalidTraceException(
                    "tick %s carries a non-finite sample: pos=(%s, %s, %s) vel=(%s, %s, %s)"
                            .formatted(index, posX, posY, posZ, velX, velY, velZ));
        }
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new InvalidTraceException(
                    "tick %s carries a non-finite rotation: yaw=%s pitch=%s".formatted(index, yaw, pitch));
        }
        if (fireworkTicksRemaining < 0) {
            throw new InvalidTraceException(
                    "tick %s has a negative firework tick count: %s".formatted(index, fireworkTicksRemaining));
        }
    }
}
