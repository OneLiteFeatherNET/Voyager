package net.elytrarace.game.player.components;

import net.elytrarace.common.ecs.Component;

import java.util.UUID;

/**
 * Component that stores player metadata for game logic.
 */
public record PlayerMetadataComponent(UUID playerId, int lastPortalIndex) implements Component {
    
    /**
     * Creates a new PlayerMetadataComponent with the given player ID and no last portal.
     */
    public static PlayerMetadataComponent create(UUID playerId) {
        return new PlayerMetadataComponent(playerId, -1);
    }
    
    /**
     * Creates a new PlayerMetadataComponent with an updated last portal index.
     */
    public PlayerMetadataComponent withLastPortalIndex(int lastPortalIndex) {
        return new PlayerMetadataComponent(playerId, lastPortalIndex);
    }
}