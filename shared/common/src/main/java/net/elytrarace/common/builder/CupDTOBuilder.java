package net.elytrarace.common.builder;

import net.elytrarace.common.cup.model.CupDTO;
import net.elytrarace.common.cup.model.FileCupDTO;
import net.elytrarace.common.map.model.FileMapDTO;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Builder for a cup DTO.
 * <p>
 *     The builder is used to create a cup DTO with the desired properties.
 *     The builder is created by calling the {@link #create()} method.
 * </p>
 * @since 1.0.0
 * @version 1.0.0
 * @see CupDTO
 * @author TheMeinerLP
 */
public sealed interface CupDTOBuilder {

    /**
     * Create a new instance of the builder.
     *
     * @return The builder.
     */
    @Contract(value = " -> new", pure = true)
    @NotNull
    static CupDTOBuilder create() {
        return new CupDTOBuilderImpl();
    }

    /**
     * Set the name of the cup.
     *
     * @param name The name of the cup.
     * @return The builder.
     */
    CupDTOBuilder name(@NotNull Key name);

    /**
     * Set the display name of the cup.
     *
     * @param displayName The display name of the cup.
     * @return The builder.
     */
    CupDTOBuilder displayName(@NotNull Component displayName);

    /**
     * Set the maps of the cup by their UUIDs.
     *
     * @param maps The UUIDs of the maps.
     * @return The builder.
     */
    CupDTOBuilder mapsByUUIDs(@NotNull List<@NotNull UUID> maps);

    /**
     * Set the maps of the cup.
     *
     * @param maps The maps of the cup.
     * @return The builder.
     */
    CupDTOBuilder maps(@NotNull List<@NotNull FileMapDTO> maps);

    /**
     * Set the maps of the cup by their UUIDs.
     *
     * @param maps The UUIDs of the maps.
     * @return The builder.
     */
    CupDTOBuilder mapsByUUIDs(@NotNull UUID... maps);

    /**
     * Set the maps of the cup.
     *
     * @param maps The maps of the cup.
     * @return The builder.
     */
    CupDTOBuilder maps(@NotNull FileMapDTO... maps);


    /**
     * Build the cup DTO.
     *
     * @return The cup DTO.
     */
    @Contract(" -> new")
    @NotNull
    CupDTO build();



    final class CupDTOBuilderImpl implements CupDTOBuilder {

        private Key name;
        private Component displayName;
        private List<UUID> maps = new ArrayList<>();

        @Override
        public CupDTOBuilder name(@NotNull Key name) {
            this.name = name;
            return this;
        }

        @Override
        public CupDTOBuilder displayName(@NotNull Component displayName) {
            this.displayName = displayName;
            return this;
        }

        @Override
        public CupDTOBuilder mapsByUUIDs(@NotNull List<UUID> maps) {
            this.maps = maps;
            return this;
        }

        @Override
        public CupDTOBuilder maps(@NotNull List<FileMapDTO> maps) {
            this.maps = maps.stream().map(FileMapDTO::uuid).toList();
            return this;
        }

        @Override
        public CupDTOBuilder mapsByUUIDs(UUID... maps) {
            this.maps = Arrays.asList(maps);
            return this;
        }

        @Override
        public CupDTOBuilder maps(FileMapDTO... maps) {
            this.maps = Stream.of(maps).map(FileMapDTO::uuid).toList();
            return this;
        }

        @Override
        public @NotNull CupDTO build() {
            return new FileCupDTO(name, displayName, maps);
        }

    }
}
