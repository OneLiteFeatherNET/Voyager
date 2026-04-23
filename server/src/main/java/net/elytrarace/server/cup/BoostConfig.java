package net.elytrarace.server.cup;

/**
 * Per-map firework boost configuration.
 * <p>
 * Boost behaviour mirrors the vanilla firework formula applied every tick
 * while the burn is active (see docs/elytra-physics-reference.md §3.2):
 * <pre>
 *   newVel = 0.5 * currentVel + 0.85 * lookDirection
 * </pre>
 * This naturally delivers a strong initial impulse (~0.85 b/t from rest),
 * converges to a steady state of ~1.7 b/t, and allows full steering because
 * the look direction is re-read every tick.
 *
 * @param burnDurationTicks     number of ticks the formula is applied; default 24 (1.2 s at 20 TPS)
 * @param maxSpeedBlocksPerTick velocity magnitude cap during boost (b/t); default 3.5 (70 m/s)
 * @param cooldownMs            minimum milliseconds between two boosts for the same player
 */
public record BoostConfig(
        int    burnDurationTicks,
        double maxSpeedBlocksPerTick,
        long   cooldownMs
) {
    public static final BoostConfig DEFAULT = new BoostConfig(24, 3.5, 4_000);
}
