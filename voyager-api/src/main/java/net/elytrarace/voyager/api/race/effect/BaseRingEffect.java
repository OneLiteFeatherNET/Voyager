package net.elytrarace.voyager.api.race.effect;

/**
 * The controlled extension point for {@link RingEffect}. New effect behaviour is added by
 * extending this class rather than implementing {@link RingEffect} directly, so {@code
 * voyager-race} — and later {@code voyager-platform} — can write a new effect without widening
 * {@link RingEffect}'s {@code permits} list.
 *
 * <p>Writing one is half the job: an effect only reaches a ring once the ring type it applies to is
 * mapped to it, and that mapping is a fixed table in {@code voyager-race}'s
 * {@code RingEffectRegistry}. There is deliberately no runtime registration — see that class for
 * why, and for the condition under which it should be reconsidered. So "extend without touching
 * {@code permits}" is what this class buys; it is not a plug-in point a module can wire into from
 * outside.
 */
public non-sealed abstract class BaseRingEffect implements RingEffect {
}
