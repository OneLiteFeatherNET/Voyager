package net.elytrarace.common.cup;

import net.elytrarace.common.cup.model.CupDTO;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Contract;

import java.util.concurrent.CompletableFuture;

/**
 * Service for managing {@link CupDTO} objects.
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
    CompletableFuture<CupDTO> getCupByName(String name);

    @Contract("_ -> new")
    static CupService create(JavaPlugin plugin) {
        return new CupServiceImpl(plugin);
    }

}
