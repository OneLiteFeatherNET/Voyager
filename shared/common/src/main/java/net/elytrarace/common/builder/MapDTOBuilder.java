package net.elytrarace.common.builder;

import net.elytrarace.common.map.model.FileMapDTO;
import net.elytrarace.common.map.model.MapDTO;
import net.elytrarace.common.map.model.FilePortalDTO;
import net.elytrarace.common.map.model.PortalDTO;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Builder for a map DTO.
 * <p>
 *     The builder is used to create a map DTO with the desired properties.
 *     The builder is created by calling the {@link #create()} method.
 *</p>
 * @since 1.0.0
 * @version 1.0.0
 * @see FileMapDTO
 */
public sealed interface MapDTOBuilder {

    /**
     * Creates a new instance of the map builder
     * @return the map builder
     */
    @Contract(value = " -> new", pure = true)
    static MapDTOBuilder create() {
        return new MapDTOBuilderImpl();
    }

    /**
     * Sets the name of the map
     * @param name the name of the map
     * @return the builder
     */
    MapDTOBuilder name(@NotNull Key name);

    /**
     * Sets the portals of the map
     * @param portals the portals of the map
     * @return the builder
     */
    MapDTOBuilder portals(@NotNull SortedSet<@NotNull PortalDTO> portals);

    /**
     * Sets the portals of the map
     * @param portals the portals of the map
     * @return the builder
     */
    MapDTOBuilder portals(@NotNull PortalDTO... portals);

    /**
     * Generates a random UUID for the map
     * @return the builder
     */
    MapDTOBuilder generateUUID();

    /**
     * Sets the UUID of the map
     * @param uuid the UUID of the map
     * @return the builder
     */
    MapDTOBuilder useUUID(@NotNull UUID uuid);

    /**
     * Sets the world of the map
     * @param world the world of the map
     * @return the builder
     */
    MapDTOBuilder world(@NotNull String world);

    /**
     * Sets the display name of the map
     * @param displayName the display name of the map
     * @return the builder
     */
    MapDTOBuilder displayName(@NotNull Component displayName);

    /**
     * Sets the author of the map
     * @param author the author of the map
     * @return the builder
     */
    MapDTOBuilder author(@NotNull Component author);

    /**
     * Copies the properties of the given map to the builder
     * @param map the map to copy the properties from
     * @return the builder
     */
    MapDTOBuilder from(@NotNull MapDTO map);

    /**
     * Builds the map
     * @return the map
     */
    @Contract(" -> new")
    @NotNull
    MapDTO build();


    final class MapDTOBuilderImpl implements MapDTOBuilder {
        private Key name;
        private SortedSet<PortalDTO> portals = new TreeSet<>();
        private UUID uuid;
        private String world;
        private Component displayName;
        private Component author;

        public MapDTOBuilder name(@NotNull Key name) {
            this.name = name;
            return this;
        }

        public MapDTOBuilder portals(@NotNull SortedSet<@NotNull PortalDTO> portals) {
            this.portals = portals;
            return this;
        }

        public MapDTOBuilder portals(@NotNull PortalDTO... portals) {
            this.portals = new TreeSet<>(List.of(portals));
            return this;
        }

        public MapDTOBuilder generateUUID() {
            this.uuid = UUID.randomUUID();
            return this;
        }

        public MapDTOBuilder useUUID(@NotNull UUID uuid) {
            this.uuid = uuid;
            return this;
        }

        public MapDTOBuilder world(@NotNull String world) {
            this.world = world;
            return this;
        }

        public MapDTOBuilder displayName(@NotNull Component displayName) {
            this.displayName = displayName;
            return this;
        }

        public MapDTOBuilder author(@NotNull Component author) {
            this.author = author;
            return this;
        }

        @Override
        public MapDTOBuilder from(@NotNull MapDTO map) {
            this.name = map.name();
            this.portals = map.portals();
            this.uuid = map.uuid();
            this.world = map.world();
            this.displayName = map.displayName();
            this.author = map.author();
            return this;
        }

        public @NotNull MapDTO build() {
            return new FileMapDTO(uuid, name, world, displayName, author, portals);
        }
    }
}
