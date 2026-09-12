package net.elytrarace.tools.converter.legacy;

import org.jetbrains.annotations.Nullable;

/**
 * The old {@code boostConfig} block, in the union of the two shapes the repository actually holds.
 *
 * <p>They disagree, which is why this is a union and not a copy. The real
 * {@code ElytraraceBlueAndRed} carries {@code {"speedBlocksPerTick": 1.7, "cooldownMs": 2000}} — a
 * cooldown and no burn at all — while the three synthetic maps carry
 * {@code {"burnDurationTicks": 24, "maxSpeedBlocksPerTick": 3.2, "cooldownMs": 3500}}. So every field
 * here is nullable and the converter seeds what a given file does not carry.
 *
 * <p><strong>The two speeds are deliberately absent.</strong> They existed because the old server
 * computed the boost itself and pushed the result with {@code setVelocity}; the rebuild lets the
 * client boost itself with Vanilla's own impulse, which has no tuning knob. Carrying them would put
 * two numbers into the new file that nothing reads — data that looks authoritative and is inert.
 */
public record LegacyBoostConfig(@Nullable Integer burnDurationTicks, @Nullable Long cooldownMs) {
}
