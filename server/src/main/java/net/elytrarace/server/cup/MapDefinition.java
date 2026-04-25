package net.elytrarace.server.cup;

import net.elytrarace.common.map.model.GuidePointDTO;
import net.elytrarace.server.physics.Ring;
import net.minestom.server.coordinate.Pos;

import java.nio.file.Path;
import java.util.List;

/**
 * Defines a single map within a cup, including its world location, ring checkpoints,
 * player spawn position, per-map boost configuration, optional guide points for spline
 * path visualization, and a reference completion time used to calibrate medal brackets.
 *
 * @param name                the display name of the map
 * @param worldDirectory      the path to the world directory on disk
 * @param rings               the ordered list of ring checkpoints players must fly through
 * @param spawnPos            the position where players spawn at the start of this map
 * @param boostConfig         firework boost settings for this map; defaults to {@link BoostConfig#DEFAULT}
 * @param guidePoints         optional guide points for shaping the ideal racing line between rings
 * @param referenceDurationMs the target completion time in milliseconds used as the
 *                            anchor for medal brackets (diamond/gold/silver/bronze).
 *                            Defaults to {@link #DEFAULT_REFERENCE_DURATION_MS} (3 minutes),
 *                            which covers a typical map at moderate skill.
 */
public record MapDefinition(String name, Path worldDirectory, List<Ring> rings, Pos spawnPos,
                            BoostConfig boostConfig, List<GuidePointDTO> guidePoints,
                            long referenceDurationMs) {

    /** Default reference completion time used when the map JSON omits the field (3 minutes). */
    public static final long DEFAULT_REFERENCE_DURATION_MS = 180_000L;

    public MapDefinition {
        rings = List.copyOf(rings);
        guidePoints = List.copyOf(guidePoints);
        if (referenceDurationMs <= 0) {
            throw new IllegalArgumentException(
                    "referenceDurationMs must be positive: " + referenceDurationMs);
        }
    }

    /** Convenience constructor using the default boost configuration and reference duration. */
    public MapDefinition(String name, Path worldDirectory, List<Ring> rings, Pos spawnPos) {
        this(name, worldDirectory, rings, spawnPos, BoostConfig.DEFAULT, List.of(), DEFAULT_REFERENCE_DURATION_MS);
    }

    /** Convenience constructor with boost config, no guide points, default reference duration. */
    public MapDefinition(String name, Path worldDirectory, List<Ring> rings, Pos spawnPos, BoostConfig boostConfig) {
        this(name, worldDirectory, rings, spawnPos, boostConfig, List.of(), DEFAULT_REFERENCE_DURATION_MS);
    }

    /** Convenience constructor that defaults the reference duration. */
    public MapDefinition(String name, Path worldDirectory, List<Ring> rings, Pos spawnPos,
                         BoostConfig boostConfig, List<GuidePointDTO> guidePoints) {
        this(name, worldDirectory, rings, spawnPos, boostConfig, guidePoints, DEFAULT_REFERENCE_DURATION_MS);
    }
}
