package net.elytrarace.api.database.repository;

import net.elytrarace.api.database.model.ElytraPlayerEntity;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface ElytraPlayerRepository {

    /**
     * Retrieves an ElytraPlayer by its UUID
     *
     * @param playerId The UUID of the player
     * @return A CompletableFuture containing the ElytraPlayer
     */
    CompletableFuture<ElytraPlayerEntity> getElytraPlayerById(UUID playerId);

    /**
     * Saves an ElytraPlayer to the database
     *
     * @param elytraPlayer The ElytraPlayer to save
     * @return A CompletableFuture that completes when the ElytraPlayer is saved
     */
    CompletableFuture<Void> saveElytraPlayer(ElytraPlayerEntity elytraPlayer);

    /**
     * Deletes an ElytraPlayer from the database
     *
     * @param elytraPlayer The ElytraPlayer to delete
     * @return A CompletableFuture that completes when the ElytraPlayer is deleted
     */
    CompletableFuture<Void> deleteElytraPlayer(ElytraPlayerEntity elytraPlayer);

    /**
     * Updates an ElytraPlayer in the database
     *
     * @param elytraPlayer The ElytraPlayer to update
     * @return A CompletableFuture that completes when the ElytraPlayer is updated
     */
    CompletableFuture<Void> updateElytraPlayer(ElytraPlayerEntity elytraPlayer);

}
