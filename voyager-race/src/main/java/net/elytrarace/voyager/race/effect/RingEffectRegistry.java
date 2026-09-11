package net.elytrarace.voyager.race.effect;

import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.api.race.effect.RingEffect;

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
 * <p><strong>The mapping is fixed, and there is no {@code register}.</strong> Design rule 3 asks a
 * registry to be backed by a {@code ConcurrentHashMap}; this one was, and nothing could ever write
 * to it, which made the concurrent map decoration and the unmodifiable view a wrapper around a map
 * nobody mutates. Adding a {@code register} instead would have been an extension point with no
 * caller — every ring type this game has is known here, and a new effect arrives as a new
 * {@link RingType} plus a line in {@code DefaultRingEffectRegistry}, in the one place the whole
 * type-to-effect mapping can be read at once. If a later stage genuinely needs to register an
 * effect it does not own — {@code voyager-platform} is the candidate — that is the point to reopen
 * this, with the caller in hand.
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

    /**
     * The one {@link RingEffectRegistry} implementation. Its mapping is built once and immutable,
     * which is what makes it safe to read from any thread — a guarantee a concurrent map would only
     * have needed if something could write.
     */
    final class DefaultRingEffectRegistry implements RingEffectRegistry {

        /** Velocity multiplier a {@code BOOST} ring leaves behind. */
        static final double BOOST_MULTIPLIER = 1.5;

        /** Velocity multiplier a {@code SLOW} ring leaves behind. */
        static final double SLOW_MULTIPLIER = 0.5;

        private final Map<RingType, RingEffect> effects = Map.of(
                RingType.BOOST, new SpeedMultiplierEffect(BOOST_MULTIPLIER),
                RingType.SLOW, new SpeedMultiplierEffect(SLOW_MULTIPLIER));

        DefaultRingEffectRegistry() {
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
