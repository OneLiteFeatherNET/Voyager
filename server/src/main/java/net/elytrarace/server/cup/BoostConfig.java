package net.elytrarace.server.cup;

/**
 * Per-map firework boost configuration.
 * <p>
 * Boost behaviour is a two-phase model (see research: firework-boost-ecs-migration):
 * <ol>
 *   <li><b>Attack</b> — an additive kick applied once at activation, in the player's
 *       current look direction.</li>
 *   <li><b>Sustain</b> — per-tick additive thrust applied for {@link #burnDurationTicks}
 *       ticks, re-reading the look direction each tick so players can steer mid-boost.
 *       Thrust fades linearly from 100 % to 30 % over the burn window.</li>
 * </ol>
 * The total velocity magnitude is capped at {@link #maxSpeedBlocksPerTick} at every step.
 * All units that end in {@code BlocksPerTick} are blocks/tick; multiply by 20 to get m/s.
 *
 * @param kickBlocksPerTick     additive speed impulse at activation (b/t); default 0.5 (10 m/s)
 * @param burnDurationTicks     number of ticks sustained thrust lasts; default 24 (1.2 s)
 * @param thrustBlocksPerTick   per-tick thrust at full falloff (b/t); default 0.035 (0.7 m/s)
 * @param maxSpeedBlocksPerTick velocity magnitude cap during boost (b/t); default 2.75 (55 m/s)
 * @param cooldownMs            minimum milliseconds between two boosts for the same player
 */
public record BoostConfig(
        double kickBlocksPerTick,
        int    burnDurationTicks,
        double thrustBlocksPerTick,
        double maxSpeedBlocksPerTick,
        long   cooldownMs
) {
    /**
     * Default values derived from the game-design spec (see docs/decisions/0001-firework-boost-in-ecs.md):
     * 10 m/s kick, 1.2 s burn, 0.7 m/s/tick thrust, 55 m/s cap, 4 s cooldown.
     */
    public static final BoostConfig DEFAULT = new BoostConfig(0.5, 24, 0.035, 2.75, 4_000);
}
