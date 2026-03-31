package net.elytrarace.server.cup;

/**
 * Per-map firework boost configuration.
 * <p>
 * All values can be tuned in the cup/map JSON files so different maps
 * can feel faster or more restricted. The boost direction is always derived
 * from the player's full look direction (yaw + pitch), so players control
 * where the boost pushes them.
 * <p>
 * The boost is applied as a single one-shot impulse — the client's own
 * elytra physics handle drag, gravity and steering from that point on.
 *
 * @param speedBlocksPerTick  total boost speed in blocks/tick (higher = faster launch)
 * @param cooldownMs          minimum milliseconds between two boosts for one player
 */
public record BoostConfig(
        double speedBlocksPerTick,
        long cooldownMs
) {
    /**
     * Defaults based on game design spec:
     * 2.5 b/t one-shot impulse, 4 s cooldown.
     */
    public static final BoostConfig DEFAULT = new BoostConfig(2.5, 4_000);
}
