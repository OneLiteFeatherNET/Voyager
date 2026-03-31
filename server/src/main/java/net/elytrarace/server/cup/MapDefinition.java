package net.elytrarace.server.cup;

import net.elytrarace.server.physics.Ring;
import net.minestom.server.coordinate.Pos;

import java.nio.file.Path;
import java.util.List;

/**
 * Defines a single map within a cup, including its world location, ring checkpoints,
 * player spawn position, and per-map boost configuration.
 *
 * @param name           the display name of the map
 * @param worldDirectory the path to the world directory on disk
 * @param rings          the ordered list of ring checkpoints players must fly through
 * @param spawnPos       the position where players spawn at the start of this map
 * @param boostConfig    firework boost settings for this map; defaults to {@link BoostConfig#DEFAULT}
 */
public record MapDefinition(String name, Path worldDirectory, List<Ring> rings, Pos spawnPos, BoostConfig boostConfig) {
    public MapDefinition {
        rings = List.copyOf(rings);
    }

    /** Convenience constructor using the default boost configuration. */
    public MapDefinition(String name, Path worldDirectory, List<Ring> rings, Pos spawnPos) {
        this(name, worldDirectory, rings, spawnPos, BoostConfig.DEFAULT);
    }
}
