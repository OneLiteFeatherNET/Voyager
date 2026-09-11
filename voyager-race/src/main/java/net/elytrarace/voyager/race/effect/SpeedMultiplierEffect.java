package net.elytrarace.voyager.race.effect;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.effect.BaseRingEffect;

/**
 * Scales every component of a velocity by a fixed factor. {@link RingEffectRegistry} wires this up
 * with 1.5 for {@code BOOST} rings and 0.5 for {@code SLOW} rings.
 */
public final class SpeedMultiplierEffect extends BaseRingEffect {

    private final double factor;

    public SpeedMultiplierEffect(double factor) {
        this.factor = factor;
    }

    @Override
    public Vec3 apply(Vec3 velocity) {
        return velocity.scale(factor);
    }
}
