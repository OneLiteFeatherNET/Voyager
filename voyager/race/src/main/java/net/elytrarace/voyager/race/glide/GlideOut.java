package net.elytrarace.voyager.race.glide;

import net.elytrarace.voyager.api.math.Vec3;

/**
 * The braking a racer's flight takes after a finish, or after a map's time limit runs out while they are still in the
 * air: their speed is damped over {@link #TICKS} ticks, down to a quarter of the speed they had on the tick the glide
 * began.
 *
 * <p>The speed at tick {@code t} is {@code v0 * 0.25^(t / 50)}, where {@code v0} is the velocity on the tick the glide
 * began. The factor is applied to the velocity, so the direction is kept and the speed never reaches zero during the
 * window: a racer who finished is still flying, and nothing reverses them. The curve is pure, so it is decided here
 * with no server, and the platform sends each tick's velocity through {@code VelocityExit}.
 *
 * <p>The damping is a first estimate. Its target and its length are the owner's to tune after a playtest; see
 * {@code design.md} in the change.
 *
 * @param finishVelocity the velocity on the tick the glide began, in blocks per tick
 */
public record GlideOut(Vec3 finishVelocity) {

    /** How many ticks the glide lasts: 2.5 seconds at 20 ticks per second. */
    public static final int TICKS = 50;

    /** The fraction of the finish speed that remains at the end of the glide. */
    private static final double END_FRACTION = 0.25;

    /**
     * The velocity on tick {@code tick} of the glide.
     *
     * @param tick the ticks since the glide began, from zero (the finish velocity itself) to {@link #TICKS}
     * @return the damped velocity, in blocks per tick, in the direction of the finish velocity
     * @throws IllegalArgumentException if {@code tick} lies outside zero to {@link #TICKS}
     */
    public Vec3 velocityAt(int tick) {
        if (tick < 0 || tick > TICKS) {
            throw new IllegalArgumentException(
                    "a glide-out has ticks 0 to %s, was tick %s".formatted(TICKS, tick));
        }
        double factor = Math.pow(END_FRACTION, (double) tick / TICKS);
        return finishVelocity.scale(factor);
    }
}
