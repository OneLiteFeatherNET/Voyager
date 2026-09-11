package net.elytrarace.tools.recorder.capture;

import net.elytrarace.tools.recorder.format.exception.InvalidTraceException;

/**
 * One tick's raw observation of the recorded glider, before it is given a place in the trace.
 *
 * <p>Mirrors {@link net.elytrarace.tools.recorder.format.TraceTick} minus the index — the index is
 * {@link TraceCollector}'s job to assign, not the sampling site's, so this carries no ordering of
 * its own. Position and velocity are {@code double} and rotation is {@code float}, matching Vanilla's
 * own numeric types.
 *
 * <p>{@code entityTick} is the glider's own {@code Entity#getTicksLived()} at sample time —
 * deliberately the entity's own counter, not the server's global tick count. The server's counter
 * advances exactly once between two scheduler heartbeats no matter what; two samples taken from
 * consecutive heartbeats would carry consecutive server ticks even if this specific entity was never
 * ticked in between; comparing it against the last recorded entityTick proves nothing. The entity's
 * own counter only advances when the entity itself was ticked, which is the fact this module actually
 * needs. {@link TraceCollector#record} enforces that consecutively recorded samples carry
 * consecutive {@code entityTick} values, turning "was this a genuine response to its input" from a
 * guess made by comparing fields that can legitimately repeat (or change for unrelated reasons, like
 * a scripted rotation change) into a fact checked against the one counter that cannot lie about it.
 */
public record GliderSample(
        double posX, double posY, double posZ,
        double velX, double velY, double velZ,
        float yaw, float pitch,
        boolean onGround,
        boolean fireworkBoostActive,
        int fireworkTicksRemaining,
        int entityTick) {

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
        if (entityTick < 0) {
            throw new InvalidTraceException("sample has a negative entity tick: %s".formatted(entityTick));
        }
    }
}
