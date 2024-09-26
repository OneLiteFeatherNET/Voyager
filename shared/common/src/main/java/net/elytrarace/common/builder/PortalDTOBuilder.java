package net.elytrarace.common.builder;

import net.elytrarace.common.map.model.LocationDTO;
import net.elytrarace.common.map.model.PortalDTO;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public sealed interface PortalDTOBuilder {

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
     * Builds the portal
     * @return the portal
     */
    @Contract(" -> new")
    @NotNull
    PortalDTO build();

    /**
     * Creates a new instance of the portal builder
     * @return the portal builder
     */
    @Contract(value = " -> new", pure = true)
    static PortalDTOBuilder create() {
        return new PortalDTOBuilderImpl();
    }

    final class PortalDTOBuilderImpl implements PortalDTOBuilder {
        private int index;
        private List<LocationDTO> locations = List.of();

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

        public @NotNull PortalDTO build() {
            return new PortalDTO(index, locations);
        }
    }
}
