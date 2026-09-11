package net.elytrarace.voyager.api.race.effect;

/**
 * The controlled extension point for {@link RingEffect}. New effect behaviour is added by
 * extending this class rather than implementing {@link RingEffect} directly, so {@code
 * voyager-race} — and later {@code voyager-platform} — can grow the set of effects without
 * widening {@link RingEffect}'s {@code permits} list.
 */
public non-sealed abstract class BaseRingEffect implements RingEffect {
}
