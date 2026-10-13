package net.elytrarace.voyager.api.race.effect;

import net.elytrarace.voyager.api.race.effect.exception.InvalidRingEffectException;

/**
 * The velocity factors a map's {@code BOOST} and {@code SLOW} rings apply. A map states them in its
 * {@code ringEffects} object; a map that states none gets {@link #DEFAULT}, the factors that were
 * hard-coded before they were per map.
 *
 * <p>A factor is finite and zero or more. A negative one reverses a racer rather than slowing them,
 * which is refused here, naming the field, rather than left to the effect that would apply it.
 *
 * @param boostMultiplier the factor a {@code BOOST} ring applies to the racer's velocity
 * @param slowMultiplier the factor a {@code SLOW} ring applies to the racer's velocity
 */
public record RingEffectTuning(double boostMultiplier, double slowMultiplier) {

    /** The factors every map used before ring effects were per map: 1.5 for {@code BOOST}, 0.5 for {@code SLOW}. */
    public static final RingEffectTuning DEFAULT = new RingEffectTuning(1.5, 0.5);

    public RingEffectTuning {
        if (!Double.isFinite(boostMultiplier) || boostMultiplier < 0.0) {
            throw InvalidRingEffectException.factor("boostMultiplier", boostMultiplier);
        }
        if (!Double.isFinite(slowMultiplier) || slowMultiplier < 0.0) {
            throw InvalidRingEffectException.factor("slowMultiplier", slowMultiplier);
        }
    }
}
