package net.elytrarace.voyager.race.com;

import org.jetbrains.annotations.ApiStatus;

/**
 * What a boost does to a computer racer's target speed on the tick it takes effect.
 *
 * <p>This is the brain's own port, not the ring-effect registry: the brain takes the effect as a parameter so that
 * a test can supply a fake, and the composition root can bridge the real effect later. The speed still moves toward
 * the changed target under the acceleration bound, so a boost never jumps the speed.
 */
@FunctionalInterface
@ApiStatus.Internal
public interface ComSpeedEffect {

    /** The effect of no boost at all: the target is left as it is. */
    ComSpeedEffect NONE = targetSpeed -> targetSpeed;

    /**
     * The target speed after the boost.
     *
     * @param targetSpeed the target speed before the boost, in blocks per tick
     * @return the target speed after the boost, in blocks per tick
     */
    double apply(double targetSpeed);
}
