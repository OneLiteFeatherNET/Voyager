package net.elytrarace.common.builder;

import net.elytrarace.common.map.model.LocationDTO;
import net.elytrarace.common.map.model.FilePortalDTO;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Builder for a portal DTO.
 * <p>
 *     The builder is used to create a portal DTO with the desired properties.
 *     The builder is created by calling the {@link #create()} method.
 * </p>
 * @since 1.0.0
 * @version 1.0.0
 * @see FilePortalDTO
 */
public sealed interface PortalDTOBuilder {

    /**
     * Creates a new instance of the portal builder
     * @return the portal builder
     */
    @Contract(value = " -> new", pure = true)
    static PortalDTOBuilder create() {
        return new PortalDTOBuilderImpl();
    }


    /**
     * Sets the index of the portal
     * @param index the index of the portal
     * @return the builder
     */
    PortalDTOBuilder index(int index);

    /**
     * Sets the locations of the portal
     * @param locations the locations of the portal
     * @return the builder
     */
    PortalDTOBuilder locations(@NotNull List<@NotNull LocationDTO> locations);

    /**
     * Sets the locations of the portal
     * @param locations the locations of the portal
     * @return the builder
     */
    PortalDTOBuilder locations(@NotNull LocationDTO... locations);

    /**
     * Sets the portal type as a raw string (e.g. "STANDARD", "BOOST"). May be {@code null}
     * to indicate an untyped portal; consumers then fall back to their own default.
     * @param type the portal type identifier, or {@code null}
     * @return the builder
     */
    PortalDTOBuilder type(String type);

    /**
     * Builds the portal
     * @return the portal
     */
    @Contract(" -> new")
    @NotNull
    FilePortalDTO build();


    final class PortalDTOBuilderImpl implements PortalDTOBuilder {
        private int index;
        private List<LocationDTO> locations = List.of();
        private String type;

        public PortalDTOBuilder index(int index) {
            this.index = index;
            return this;
        }

        public PortalDTOBuilder locations(@NotNull List<LocationDTO> locations) {
            this.locations = locations;
            return this;
        }

        public PortalDTOBuilder locations(LocationDTO... locations) {
            this.locations = List.of(locations);
            return this;
        }

        public PortalDTOBuilder type(String type) {
            this.type = type;
            return this;
        }

        public @NotNull FilePortalDTO build() {
            return new FilePortalDTO(index, locations, type);
        }
    }
}
