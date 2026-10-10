package net.elytrarace.game.components;

import net.elytrarace.common.ecs.Component;
import net.kyori.adventure.key.Key;

import java.util.List;

/**
 * Component that stores cup data directly.
 * This is part of the flattened architecture, replacing the CupComponent that wraps a ResolvedCupDTO.
 */
public record SimpleCupComponent(
    Key cupId,
    net.kyori.adventure.text.Component displayName,
    List<Key> mapIds
) implements Component {
    
    /**
     * Creates a new SimpleCupComponent with the given cup ID, display name, and map IDs.
     */
    public static SimpleCupComponent create(Key cupId, net.kyori.adventure.text.Component displayName, List<Key> mapIds) {
        return new SimpleCupComponent(cupId, displayName, mapIds);
    }
    
    /**
     * Gets the cup ID.
     */
    public Key getCupId() {
        return cupId;
    }
    
    /**
     * Gets the display name.
     */
    public net.kyori.adventure.text.Component getDisplayName() {
        return displayName;
    }
    
    /**
     * Gets the map IDs.
     */
    public List<Key> getMapIds() {
        return mapIds;
    }
    
    /**
     * Gets a map ID by index.
     */
    public Key getMapId(int index) {
        if (index < 0 || index >= mapIds.size()) {
            return null;
        }
        return mapIds.get(index);
    }
    
    /**
     * Gets the first map ID.
     */
    public Key getFirstMapId() {
        if (mapIds.isEmpty()) {
            return null;
        }
        return mapIds.get(0);
    }
}