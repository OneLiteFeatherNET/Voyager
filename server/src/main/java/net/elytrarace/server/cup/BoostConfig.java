package net.elytrarace.server.cup;

/**
 * Per-map firework boost configuration.
 * <p>
 * All values can be tuned in the cup/map JSON files so different maps
 * can feel faster, higher, or more restricted.
 *
 * @param speedBlocksPerTick  total boost speed in blocks/tick (higher = faster launch)
 * @param upAngleDeg          angle above horizontal in degrees; 0° = fully horizontal,
 *                            90° = straight up; 25° is a good default for height gain
 * @param durationTicks       how many ticks the boost is sustained (20 ticks = 1 second)
 * @param cooldownMs          minimum milliseconds between two boosts for one player
 */
public record BoostConfig(
        double speedBlocksPerTick,
        double upAngleDeg,
        int durationTicks,
        long cooldownMs
) {
    /** Sensible defaults: fast launch, 25° upward tilt, 1-second sustain, 3-second cooldown. */
    public static final BoostConfig DEFAULT = new BoostConfig(2.5, 25.0, 20, 3_000);
}
