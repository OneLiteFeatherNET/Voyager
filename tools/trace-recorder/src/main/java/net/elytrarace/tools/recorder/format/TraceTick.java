package net.elytrarace.tools.recorder.format;

import net.elytrarace.tools.recorder.format.exception.InvalidTraceException;

/**
 * One tick of a recorded glide: the state produced by applying this index's scripted input and
 * letting exactly one world tick pass, sampled after that tick — not before it, and not after two.
 *
 * <p>Position and velocity are {@code double} and rotation is {@code float}, mirroring Vanilla's own
 * numeric types. Velocity is the entity's real internal delta movement, not a position difference.
 *
 * <p>{@code entityTick} carries the glider's own {@code Entity#getTicksLived()} at sample time (see
 * {@link net.elytrarace.tools.recorder.capture.GliderSample} for why that counter and not the
 * server's global one). {@code index} is this trace's own 0-based position and is always
 * consecutive by construction; {@code entityTick} is the independent, externally-verifiable fact
 * that the tick loop actually reached this entity between one recorded sample and the next — a gap
 * between two consecutive {@code index} values' {@code entityTick} means a real physics transition
 * is missing from the trace, silently, unless something checks for it.
 * {@link net.elytrarace.tools.recorder.capture.TraceCollector#record} is that check.
 */
public record TraceTick(
        int index,
        double posX, double posY, double posZ,
        double velX, double velY, double velZ,
        float yaw, float pitch,
        boolean onGround,
        boolean fireworkBoostActive,
        int fireworkTicksRemaining,
        int entityTick) {

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
        if (entityTick < 0) {
            throw new InvalidTraceException("tick %s has a negative entity tick: %s".formatted(index, entityTick));
        }
    }
}
