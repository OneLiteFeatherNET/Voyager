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
 *     "kickBlocksPerTick": 0.5,
 *     "burnDurationTicks": 24,
 *     "thrustBlocksPerTick": 0.035,
 *     "maxSpeedBlocksPerTick": 2.75,
 *     "cooldownMs": 4000
 *   }
 * }
 * </pre>
 *
 * @param kickBlocksPerTick     additive kick at activation (b/t); {@code null} → default
 * @param burnDurationTicks     sustained thrust duration in ticks; {@code null} → default
 * @param thrustBlocksPerTick   per-tick thrust at full falloff (b/t); {@code null} → default
 * @param maxSpeedBlocksPerTick velocity magnitude cap (b/t); {@code null} → default
 * @param cooldownMs            milliseconds between two boosts; {@code null} → default
 */
public record BoostConfigDTO(
        Double  kickBlocksPerTick,
        Integer burnDurationTicks,
        Double  thrustBlocksPerTick,
        Double  maxSpeedBlocksPerTick,
        Long    cooldownMs
) {}
