package net.elytrarace.common.builder;

import net.elytrarace.common.map.model.LocationDTO;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Builder for a location DTO.
 * <p>
 *     The builder is used to create a location DTO with the desired properties.
 *     The builder is created by calling the {@link #create()} method.
 * </p>
 * @since 1.0.0
 * @version 1.0.0
 * @see LocationDTO
 * @author TheMeinerLP
 */
public sealed interface LocationDTOBuilder {

    /**
     * Sets the x coordinate of the location
     * @param x the x coordinate of the location
     * @return the builder
     */
    LocationDTOBuilder x(int x);

    /**
     * Sets the y coordinate of the location
     * @param y the y coordinate of the location
     * @return the builder
     */
    LocationDTOBuilder y(int y);

    /**
     * Sets the z coordinate of the location
     * @param z the z coordinate of the location
     * @return the builder
     */
    LocationDTOBuilder z(int z);

    /**
     * Sets whether the location is the center of the portal
     * @param center whether the location is the center of the portal
     * @return the builder
     */
    LocationDTOBuilder center(boolean center);

    /**
     * Builds the location
     * @return the location
     */
    @Contract(" -> new")
    @NotNull
    LocationDTO build();

    /**
     * Creates a new instance of the location builder
     * @return the location builder
     */
    @Contract(value = " -> new", pure = true)
    static LocationDTOBuilder create() {
        return new LocationDTOBuilderImpl();
    }

    final class LocationDTOBuilderImpl implements LocationDTOBuilder {
        private int x;
        private int y;
        private int z;
        private boolean center;

        public LocationDTOBuilder x(int x) {
            this.x = x;
            return this;
        }

        public LocationDTOBuilder y(int y) {
            this.y = y;
            return this;
        }

        public LocationDTOBuilder z(int z) {
            this.z = z;
            return this;
        }

        public LocationDTOBuilder center(boolean center) {
            this.center = center;
            return this;
        }

        public @NotNull LocationDTO build() {
            return new LocationDTO(x, y, z, center);
        }
    }
}
