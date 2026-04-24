package net.elytrarace.common.map.model;

/**
 * JSON-serialisable representation of per-map firework boost settings.
 * <p>
 * All fields are optional in the JSON ({@code null} when absent after Gson deserialisation).
 * {@link net.elytrarace.server.cup.CupLoader} falls back field-by-field to
 * {@code BoostConfig.DEFAULT} for any missing field.
 *
 * <pre>Example map.json snippet:
 * {
 *   "boostConfig": {
 *     "burnDurationTicks": 24,
 *     "maxSpeedBlocksPerTick": 3.5,
 *     "cooldownMs": 4000
 *   }
 * }
 * </pre>
 *
 * @param burnDurationTicks     sustained burn duration in ticks; {@code null} → default
 * @param maxSpeedBlocksPerTick velocity magnitude cap (b/t); {@code null} → default
 * @param cooldownMs            milliseconds between two boosts; {@code null} → default
 */
public record BoostConfigDTO(
        Integer burnDurationTicks,
        Double  maxSpeedBlocksPerTick,
        Long    cooldownMs
) {}
