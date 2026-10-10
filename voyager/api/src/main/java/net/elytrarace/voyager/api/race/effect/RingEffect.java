package net.elytrarace.voyager.api.race.effect;

import net.elytrarace.voyager.api.math.Vec3;

/**
 * The one-shot velocity change a ring leaves behind when a player passes through it.
 *
 * <p>An effect only computes the velocity that results from applying it — it sends nothing
 * anywhere. Whether a client is told about the change, and the old tree's rule of telling it only
 * when the value actually changed, are both platform decisions that live outside this module.
 */
public sealed interface RingEffect permits BaseRingEffect {

    /** Returns the velocity that results from applying this effect to {@code velocity}. */
    Vec3 apply(Vec3 velocity);
}
