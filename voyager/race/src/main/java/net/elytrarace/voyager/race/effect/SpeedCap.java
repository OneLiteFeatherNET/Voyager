package net.elytrarace.voyager.race.effect;

import net.elytrarace.voyager.api.math.Vec3;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

/**
 * The speed cap a map may state for the velocity its ring effects write.
 *
 * <p>A pure function, so the rule can be read and tested without a server. The cap limits the
 * <em>magnitude</em> of a velocity and keeps its direction, so a racer who is over the cap is sent on
 * the same heading at the cap's speed. It is applied only to a velocity a ring effect has just
 * written; normal flight and rockets never pass through it, because the server writes nothing for
 * either.
 */
public final class SpeedCap {

    private SpeedCap() {
    }

    /**
     * Returns {@code velocity}, or the same direction at {@code maxSpeed} when it is faster than that.
     *
     * @param velocity the velocity in blocks per tick
     * @param maxSpeed the cap in blocks per tick, finite and greater than zero, or {@code null} for no cap
     * @return {@code velocity} when it is at or under the cap, or when there is no cap; otherwise the
     *     velocity scaled to the cap's length
     */
    @Contract(pure = true)
    public static Vec3 limit(Vec3 velocity, @Nullable Double maxSpeed) {
        if (maxSpeed == null) {
            return velocity;
        }
        double speed = velocity.length();
        if (speed <= maxSpeed) {
            return velocity;
        }
        return velocity.scale(maxSpeed / speed);
    }
}
