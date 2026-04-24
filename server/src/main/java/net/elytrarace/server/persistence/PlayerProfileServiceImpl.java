package net.elytrarace.server.persistence;

import net.elytrarace.api.database.entity.ElytraPlayerEntity;
import net.elytrarace.api.database.repository.ElytraPlayerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Default {@link PlayerProfileService} backed by {@link ElytraPlayerRepository}.
 * Never blocks the caller: all persistence happens on
 * {@link CompletableFuture#supplyAsync(java.util.function.Supplier)} threads.
 */
public final class PlayerProfileServiceImpl implements PlayerProfileService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerProfileServiceImpl.class);

    private final ElytraPlayerRepository repository;

    public PlayerProfileServiceImpl(ElytraPlayerRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public CompletableFuture<ElytraPlayerEntity> onPlayerJoin(UUID playerId, String username) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        Objects.requireNonNull(username, "username must not be null");

        LocalDateTime now = LocalDateTime.now();
        return repository.getElytraPlayerById(playerId)
                .thenCompose(existing -> {
                    if (existing == null) {
                        ElytraPlayerEntity fresh = new ElytraPlayerEntity(playerId);
                        fresh.setLastKnownName(username);
                        fresh.setLastPlayed(now);
                        fresh.setTotalGamesPlayed(0);
                        fresh.setTotalWins(0);
                        fresh.setTotalRingsPassed(0);
                        LOGGER.info("First join for {} ({}) — creating profile", username, playerId);
                        return repository.saveElytraPlayer(fresh).thenApply(v -> fresh);
                    }
                    // Returning player — refresh name (handles renames) and activity timestamp.
                    String previousName = existing.getLastKnownName();
                    existing.setLastKnownName(username);
                    existing.setLastPlayed(now);
                    if (previousName != null && !previousName.equals(username)) {
                        LOGGER.info("Player {} renamed from '{}' to '{}'", playerId, previousName, username);
                    }
                    return repository.updateElytraPlayer(existing).thenApply(v -> existing);
                })
                .exceptionally(ex -> {
                    LOGGER.error("Failed to upsert player profile for {} ({}): {}",
                            username, playerId, ex.getMessage(), ex);
                    // Return null: caller must treat profile as unavailable for this session.
                    return null;
                });
    }
}
