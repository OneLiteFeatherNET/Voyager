package net.elytrarace.common.cup;

import net.elytrarace.common.cup.model.CupDTO;
import net.elytrarace.common.cup.model.FileCupDTO;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing {@link CupDTO} objects.
 * <p>
 *     The service is used to get {@link CupDTO} objects by their name or to get a random {@link CupDTO} object.
 *     The service is created by calling the {@link #create(JavaPlugin)} method.
 *     The service is implemented by the {@link CupServiceImpl} class.
 * </p>
 * @since 1.0.0
 * @version 1.0.0
 * @see CupDTO
 * @see CupServiceImpl
 * @author TheMeinerLP
 */
public interface CupService {

    /**
     * Get a random {@link CupDTO} object.
     *
     * @return A {@link CompletableFuture} containing the random {@link CupDTO} object.
     */
    CompletableFuture<CupDTO> getRandomCup();

    /**
     * Get a {@link CupDTO} object by its name.
     *
     * @param name The name of the cup
     * @return A {@link CompletableFuture} containing the {@link CupDTO} object.
     */
    CompletableFuture<CupDTO> getCupByName(@NotNull String name);

    /**
     * Add a new {@link CupDTO} object.
     *
     * @param cupDTO The cup to add
     * @return A {@link CompletableFuture} containing a boolean value indicating whether the cup was added successfully.
     */
    CompletableFuture<Boolean> addCup(@NotNull CupDTO cupDTO);

    /**
     * Remove a {@link CupDTO} object.
     *
     * @param cupDTO The cup to remove
     * @return A {@link CompletableFuture} containing a boolean value indicating whether the cup was removed successfully.
     */
    CompletableFuture<Boolean> removeCup(@NotNull CupDTO cupDTO);

    /**
     * Update a {@link CupDTO} object.
     *
     * @param cupDTO The cup to update
     * @return A {@link CompletableFuture} containing a boolean value indicating whether the cup was updated successfully.
     */
    CompletableFuture<Boolean> updateCup(@NotNull CupDTO cupDTO);

    /**
     * Get all cups.
     *
     * @return A list of all cups.
     */
    List<FileCupDTO> getCups();

    /**
     * Get all cups asynchronously.
     *
     * @return A {@link CompletableFuture} containing a list of all cups.
     */
    CompletableFuture<List<FileCupDTO>> getCupsAsync();

    /**
     * Save the cups to the storage.
     *
     * @return A {@link CompletableFuture} indicating the completion of the operation.
     */
    CompletableFuture<Void> saveCups();

    /**
     * Creates a new instance of the cup service.
     *
     * @param plugin The plugin to create the service for
     * @return The cup service
     */
    @Contract("_ -> new")
    static CupService create(@NotNull JavaPlugin plugin) {
        return new CupServiceImpl(plugin);
    }

}
