package net.elytrarace.voyager.race.effect;

import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.api.race.effect.RingEffect;
import net.elytrarace.voyager.api.race.effect.RingEffectTuning;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Map;
import java.util.Optional;

/**
 * Looks up the one-shot {@link RingEffect} a {@link RingType} leaves behind, if any.
 *
 * <p>{@code STANDARD} and {@code CHECKPOINT} carry no effect of their own — the points a {@code
 * STANDARD} ring awards and the checkpoint a {@code CHECKPOINT} ring records already happen in
 * {@code voyager.race.scoring} and {@code voyager.race.progress} — so {@link
 * #effectFor(RingType)} reports their absence rather than handing back a no-op effect.
 *
 * <p><strong>There is still no {@code register}, but the registry is built per map.</strong> The
 * factors a {@code BOOST} or {@code SLOW} ring applies used to be constants here, and the only
 * callers were this class's own tests. A map now states them in its {@code ringEffects}, so the
 * platform builds one registry for each course from that map's {@link RingEffectTuning}: the
 * caller this class was waiting for. Each registry is still fixed once built. Nothing registers an
 * effect at runtime, and a new effect arrives as a new {@link RingType} plus a line in
 * {@code DefaultRingEffectRegistry}.
 */
public sealed interface RingEffectRegistry permits RingEffectRegistry.DefaultRingEffectRegistry {

    /** Creates a registry with the default factors, the ones every map used before they were per map. */
    @Contract(value = " -> new", pure = true)
    static RingEffectRegistry create() {
        return create(RingEffectTuning.DEFAULT);
    }

    /**
     * Creates a registry whose {@code BOOST} and {@code SLOW} effects apply the factors of {@code tuning}.
     *
     * @param tuning the factors of one map's {@code BOOST} and {@code SLOW} rings
     */
    @Contract(value = "_ -> new", pure = true)
    static RingEffectRegistry create(RingEffectTuning tuning) {
        return new DefaultRingEffectRegistry(tuning);
    }

    /** Returns the effect registered for {@code type}, or empty if that type carries none. */
    Optional<RingEffect> effectFor(RingType type);

    /** Every registered effect, keyed by the ring type it applies to. */
    @Unmodifiable
    Map<RingType, RingEffect> entries();

    /**
     * The one {@link RingEffectRegistry} implementation. Its mapping is built once and immutable,
     * which is what makes it safe to read from any thread — a guarantee a concurrent map would only
     * have needed if something could write.
     */
    final class DefaultRingEffectRegistry implements RingEffectRegistry {

        private final Map<RingType, RingEffect> effects;

        DefaultRingEffectRegistry(RingEffectTuning tuning) {
            this.effects = Map.of(
                    RingType.BOOST, new SpeedMultiplierEffect(tuning.boostMultiplier()),
                    RingType.SLOW, new SpeedMultiplierEffect(tuning.slowMultiplier()));
        }

        @Override
        public Optional<RingEffect> effectFor(RingType type) {
            return Optional.ofNullable(effects.get(type));
        }

        @Override
        @Unmodifiable
        public Map<RingType, RingEffect> entries() {
            return effects;
        }
    }
}
