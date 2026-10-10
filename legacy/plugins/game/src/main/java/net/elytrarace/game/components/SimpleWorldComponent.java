package net.elytrarace.game.components;

import net.elytrarace.common.ecs.Component;

/**
 * Component that stores world data directly.
 * This is part of the flattened architecture, replacing the WorldComponent that contains a Bukkit World object.
 */
public record SimpleWorldComponent(
    String worldName,
    boolean isLoaded
) implements Component {
    
    /**
     * Creates a new SimpleWorldComponent with the given world name.
     */
    public static SimpleWorldComponent create(String worldName) {
        return new SimpleWorldComponent(worldName, false);
    }
    
    /**
     * Creates a new SimpleWorldComponent with the given world name and loaded state.
     */
    public static SimpleWorldComponent create(String worldName, boolean isLoaded) {
        return new SimpleWorldComponent(worldName, isLoaded);
    }
    
    /**
     * Gets the world name.
     */
    public String getWorldName() {
        return worldName;
    }
    
    /**
     * Checks if the world is loaded.
     */
    public boolean isLoaded() {
        return isLoaded;
    }
    
    /**
     * Creates a new SimpleWorldComponent with updated loaded state.
     */
    public SimpleWorldComponent withLoaded(boolean isLoaded) {
        return new SimpleWorldComponent(worldName, isLoaded);
    }
}