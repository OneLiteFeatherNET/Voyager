package net.elytrarace.voyager.race.effect;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.effect.BaseRingEffect;

/**
 * Scales every component of a velocity by a fixed factor. {@link RingEffectRegistry} wires this up
 * with 1.5 for {@code BOOST} rings and 0.5 for {@code SLOW} rings.
 *
 * <p>The factor must be finite and non-negative. A negative one does not slow a player down, it
 * reverses them mid-flight; a non-finite one produces a velocity {@link Vec3} would reject anyway,
 * but as a {@code NonFiniteVectorException} out of the math package, one call frame away from the
 * ring effect that actually caused it. Zero is allowed: a ring that stops a player dead is a strange
 * design choice rather than an impossible value.
 *
 * <p>{@link IllegalArgumentException} rather than a domain exception, on this stage's rule: a factor
 * is written into code beside the ring type it belongs to, never read in from map data, so a bad one
 * is a programming error.
 */
public final class SpeedMultiplierEffect extends BaseRingEffect {

    private final double factor;

    public SpeedMultiplierEffect(double factor) {
        if (!Double.isFinite(factor)) {
            throw new IllegalArgumentException("speed multiplier must be finite, was %s".formatted(factor));
        }
        if (factor < 0.0) {
            throw new IllegalArgumentException(
                    "speed multiplier must not be negative, was %s".formatted(factor));
        }
        this.factor = factor;
    }

    @Override
    public Vec3 apply(Vec3 velocity) {
        return velocity.scale(factor);
    }
}
