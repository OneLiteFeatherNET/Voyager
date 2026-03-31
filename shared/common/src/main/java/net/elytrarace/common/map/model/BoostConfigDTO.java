package net.elytrarace.common.map.model;

/**
 * JSON-serialisable representation of per-map firework boost settings.
 * <p>
 * Both fields are optional in the JSON. When the field is absent Gson leaves
 * the value {@code null}, and {@link net.elytrarace.server.cup.CupLoader}
 * falls back to {@code BoostConfig.DEFAULT} for any missing field.
 *
 * <pre>Example map.json snippet:
 * {
 *   "boostConfig": {
 *     "speedBlocksPerTick": 3.0,
 *     "cooldownMs": 2000
 *   }
 * }
 * </pre>
 *
 * @param speedBlocksPerTick one-shot impulse speed in blocks/tick; {@code null} → default
 * @param cooldownMs         milliseconds between two boosts; {@code null} → default
 */
public record BoostConfigDTO(
        Double speedBlocksPerTick,
        Long cooldownMs
) {}
