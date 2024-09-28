package net.elytrarace.common.map;

import net.elytrarace.common.cup.model.CupDTO;
import net.elytrarace.common.map.model.MapDTO;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing maps
 * <p>
 *     The map service is used to get maps by their name or by their UUID.
 * </p>
 * @since 1.0.0
 * @version 1.0.0
 * @see MapDTO
 */
public interface MapService {

    /**
     * Gets a map by its name
     * @param name the name of the map
     * @return a future containing the map
     */
    CompletableFuture<MapDTO> getMapByName(@NotNull String name);

    /**
     * Resolves a maps by its UUID
     * @param cupDTO the cup to get the map from
     * @return a future containing cup with the maps
     */
    CompletableFuture<CupDTO> getMapByCup(@NotNull CupDTO cupDTO);

    /**
     * Gets a map by its UUID
     * @param uuid the UUID of the map
     * @return a future containing the map
     */
    CompletableFuture<MapDTO> getMapByUUID(@NotNull UUID uuid);

    /**
     * Adds a map to the service
     * @param mapDTO the map to add
     * @return a future containing a boolean value indicating whether the map was added successfully
     */
    CompletableFuture<Boolean> addMap(@NotNull MapDTO mapDTO);

    /**
     * Removes a map from the service
     * @param mapDTO the map to remove
     * @return a future containing a boolean value indicating whether the map was removed successfully
     */
    CompletableFuture<Boolean> removeMap(@NotNull MapDTO mapDTO);

    /**
     * Updates a map in the service
     * @param mapDTO the map to update
     * @return a future containing a boolean value indicating whether the map was updated successfully
     */
    CompletableFuture<Boolean> updateMap(@NotNull MapDTO mapDTO);

    /**
     * Gets all maps
     * @return a list of maps
     */
    List<MapDTO> getMaps();

    /**
     * Gets all maps asynchronously
     * @return a future containing a list of maps
     */
    CompletableFuture<List<MapDTO>> getMapsAsync();

    /**
     * Saves the maps to the file
     * @return a future containing a void value
     */
    CompletableFuture<Void> saveMaps();

    /**
     * Creates a new instance of the map service
     * @param plugin the plugin to create the service for
     * @return the map service
     */
    @Contract("_ -> new")
    static MapService create(@NotNull JavaPlugin plugin) {
        return new MapServiceImpl(plugin);
    }
}
