package net.elytrarace.game.components;

import net.elytrarace.common.ecs.Component;
import net.kyori.adventure.key.Key;

/**
 * Component that stores the current map information.
 * This is part of the flattened architecture, replacing part of the GameStateComponent.
 */
public record CurrentMapComponent(
    Key mapId,
    String worldName,
    int mapIndex
) implements Component {
    
    /**
     * Creates a new CurrentMapComponent with the given map ID, world name, and map index.
     */
    public static CurrentMapComponent create(Key mapId, String worldName, int mapIndex) {
        return new CurrentMapComponent(mapId, worldName, mapIndex);
    }
    
    /**
     * Creates a new CurrentMapComponent with an empty map.
     */
    public static CurrentMapComponent createEmpty() {
        return new CurrentMapComponent(null, null, -1);
    }
    
    /**
     * Checks if this component has a map.
     */
    public boolean hasMap() {
        return mapId != null && worldName != null;
    }
    
    /**
     * Creates a new CurrentMapComponent with the next map index.
     */
    public CurrentMapComponent withNextMapIndex() {
        return new CurrentMapComponent(mapId, worldName, mapIndex + 1);
    }
}