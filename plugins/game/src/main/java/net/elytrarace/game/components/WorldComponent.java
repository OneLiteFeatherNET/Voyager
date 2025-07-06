package net.elytrarace.game.components;

import net.elytrarace.common.ecs.Component;
import org.bukkit.World;

import java.util.Optional;

/**
 * Component that stores world-related data.
 */
public record WorldComponent(String worldName, World world) implements Component {
    
    /**
     * Creates a new WorldComponent with the given world name and world.
     */
    public static WorldComponent create(String worldName, World world) {
        return new WorldComponent(worldName, world);
    }
    
    /**
     * Creates a new WorldComponent with the given world.
     */
    public static WorldComponent create(World world) {
        return new WorldComponent(world.getName(), world);
    }
    
    /**
     * Gets the world name.
     */
    public String getWorldName() {
        return worldName;
    }
    
    /**
     * Gets the world, if it's loaded.
     */
    public Optional<World> getWorld() {
        return Optional.ofNullable(world);
    }
    
    /**
     * Creates a new WorldComponent with an updated world.
     */
    public WorldComponent withWorld(World world) {
        return new WorldComponent(worldName, world);
    }
}