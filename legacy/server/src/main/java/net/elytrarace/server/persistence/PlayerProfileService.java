package net.elytrarace.server.persistence;

import net.elytrarace.api.database.entity.ElytraPlayerEntity;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Upserts player profile rows ({@code elytra_players}) as players join the server.
 * <p>
 * First-time joiners get a new row. Returning players have their {@code lastKnownName}
 * and {@code lastPlayed} refreshed to track renames and activity.
 */
public interface PlayerProfileService {

    /**
     * Loads the existing profile or creates and persists a new one.
     *
     * @param playerId the player's Minecraft UUID
     * @param username the player's current Minecraft username
     * @return a future completing with the persisted entity (either loaded or freshly created)
     */
    CompletableFuture<ElytraPlayerEntity> onPlayerJoin(UUID playerId, String username);
}
