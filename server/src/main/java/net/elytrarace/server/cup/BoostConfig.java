package net.elytrarace.server.cup;

/**
 * Per-map firework boost configuration.
 * <p>
 * All values can be tuned in the cup/map JSON files so different maps
 * can feel faster or more restricted. The boost direction is always derived
 * from the player's full look direction (yaw + pitch), so players control
 * where the boost pushes them.
 *
 * @param speedBlocksPerTick  total boost speed in blocks/tick (higher = faster launch)
 * @param durationTicks       how many ticks the boost is sustained (20 ticks = 1 second)
 * @param cooldownMs          minimum milliseconds between two boosts for one player
 */
public record BoostConfig(
        double speedBlocksPerTick,
        int durationTicks,
        long cooldownMs
) {
    /**
     * Defaults based on game design spec:
     * 2.5 b/t speed, 1.5 s sustain (30 ticks), 4 s cooldown (80 ticks).
     */
    public static final BoostConfig DEFAULT = new BoostConfig(2.5, 30, 4_000);
}
