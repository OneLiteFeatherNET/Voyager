package net.elytrarace.common.map.model;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

import java.util.SortedSet;
import java.util.UUID;

/**
 * Represents a map for the game.
 * @see MapDTO
 * @see FileMapDTO
 * @see PortalDTO
 *
 * @since 1.0.0
 * @version 1.0.0
 * @author TheMeinerLP
 */
public interface MapDTO {

    /**
     * Returns the UUID of the map.
     * @return the UUID of the map
     */
    UUID uuid();

    /**
     * Returns the name of the map.
     * @return the name of the map
     */
    Key name();

    /**
     * Returns the world of the map.
     * @return the world of the map
     */
    String world();

    /**
     * Returns the display name of the map.
     * @return the display name of the map
     */
    Component displayName();

    /**
     * Returns the author of the map.
     * @return the author of the map
     */
    Component author();

    /**
     * Returns the portals of the map.
     * @return the portals of the map
     */
    SortedSet<PortalDTO> portals();
}
