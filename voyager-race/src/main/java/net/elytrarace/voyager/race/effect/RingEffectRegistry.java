package net.elytrarace.voyager.race.effect;

import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.api.race.effect.RingEffect;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Looks up the one-shot {@link RingEffect} a {@link RingType} leaves behind, if any.
 *
 * <p>{@code STANDARD} and {@code CHECKPOINT} carry no effect of their own — the points a {@code
 * STANDARD} ring awards and the checkpoint a {@code CHECKPOINT} ring records already happen in
 * {@code voyager.race.scoring} and {@code voyager.race.progress} — so {@link
 * #effectFor(RingType)} reports their absence rather than handing back a no-op effect.
 */
public sealed interface RingEffectRegistry permits RingEffectRegistry.DefaultRingEffectRegistry {

    /** Creates a new registry, pre-populated with the standard {@code BOOST}/{@code SLOW} effects. */
    @Contract(value = " -> new", pure = true)
    static RingEffectRegistry create() {
        return new DefaultRingEffectRegistry();
    }

    /** Returns the effect registered for {@code type}, or empty if that type carries none. */
    Optional<RingEffect> effectFor(RingType type);

    /** Every registered effect, keyed by the ring type it applies to. */
    @Unmodifiable
    Map<RingType, RingEffect> entries();

    /** The one {@link RingEffectRegistry} implementation, backed by a {@link ConcurrentHashMap}. */
    final class DefaultRingEffectRegistry implements RingEffectRegistry {

        /** Velocity multiplier a {@code BOOST} ring leaves behind. */
        static final double BOOST_MULTIPLIER = 1.5;

        /** Velocity multiplier a {@code SLOW} ring leaves behind. */
        static final double SLOW_MULTIPLIER = 0.5;

        private final Map<RingType, RingEffect> effects = new ConcurrentHashMap<>();

        DefaultRingEffectRegistry() {
            effects.put(RingType.BOOST, new SpeedMultiplierEffect(BOOST_MULTIPLIER));
            effects.put(RingType.SLOW, new SpeedMultiplierEffect(SLOW_MULTIPLIER));
        }

        @Override
        public Optional<RingEffect> effectFor(RingType type) {
            return Optional.ofNullable(effects.get(type));
        }

        @Override
        @Unmodifiable
        public Map<RingType, RingEffect> entries() {
            return Collections.unmodifiableMap(effects);
        }
    }
}
