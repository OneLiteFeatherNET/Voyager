package net.elytrarace.voyager.race.effect;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.effect.RingEffect;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The velocity a racer leaves a set of ring effects with: the effects applied one after another, in
 * the order the rings were crossed, and then the map's speed cap applied once to the result.
 *
 * <p>Pure, like {@link SpeedCap}. Two effects in quick succession compose, so a {@code BOOST} then a
 * {@code SLOW} leaves three quarters of the speed, and two {@code BOOST}s leave 2.25 times it unless the
 * cap is lower than that. The cap is applied once, after the whole sequence, so a sequence is never
 * clamped in the middle and then multiplied past the cap again.
 */
public final class RingEffectSequence {

    private RingEffectSequence() {
    }

    /**
     * @param velocity the velocity before the first effect, in blocks per tick
     * @param effects the effects of the rings crossed on this tick, in crossing order; empty when none
     *     of them carries an effect
     * @param maxSpeed the map's cap in blocks per tick, or {@code null} for no cap
     * @return {@code velocity} unchanged when {@code effects} is empty; otherwise the result of the effects
     *     in order, capped by {@link SpeedCap#limit(Vec3, Double)}
     */
    @Contract(pure = true)
    public static Vec3 apply(Vec3 velocity, List<RingEffect> effects, @Nullable Double maxSpeed) {
        if (effects.isEmpty()) {
            return velocity;
        }
        Vec3 result = velocity;
        for (RingEffect effect : effects) {
            result = effect.apply(result);
        }
        return SpeedCap.limit(result, maxSpeed);
    }
}
