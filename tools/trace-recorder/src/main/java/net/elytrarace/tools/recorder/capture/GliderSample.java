package net.elytrarace.tools.recorder.capture;

import net.elytrarace.tools.recorder.format.exception.InvalidTraceException;

/**
 * One tick's raw observation of the recorded glider, before it is given a place in the trace.
 *
 * <p>Mirrors {@link net.elytrarace.tools.recorder.format.TraceTick} minus the index — the index is
 * {@link TraceCollector}'s job to assign, not the sampling site's, so this carries no ordering of
 * its own. Position and velocity are {@code double} and rotation is {@code float}, matching Vanilla's
 * own numeric types.
 */
public record GliderSample(
        double posX, double posY, double posZ,
        double velX, double velY, double velZ,
        float yaw, float pitch,
        boolean onGround,
        boolean fireworkBoostActive,
        int fireworkTicksRemaining) {

    public GliderSample {
        if (!Double.isFinite(posX) || !Double.isFinite(posY) || !Double.isFinite(posZ)
                || !Double.isFinite(velX) || !Double.isFinite(velY) || !Double.isFinite(velZ)) {
            throw new InvalidTraceException(
                    "sample carries a non-finite value: pos=(%s, %s, %s) vel=(%s, %s, %s)"
                            .formatted(posX, posY, posZ, velX, velY, velZ));
        }
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new InvalidTraceException(
                    "sample carries a non-finite rotation: yaw=%s pitch=%s".formatted(yaw, pitch));
        }
        if (fireworkTicksRemaining < 0) {
            throw new InvalidTraceException(
                    "sample has a negative firework tick count: %s".formatted(fireworkTicksRemaining));
        }
    }
}
