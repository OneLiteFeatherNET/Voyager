package net.elytrarace.server.persistence;

import net.elytrarace.api.database.entity.ElytraPlayerEntity;
import net.elytrarace.api.database.entity.GameResultEntity;
import net.elytrarace.api.database.repository.ElytraPlayerRepository;
import net.elytrarace.api.database.repository.GameResultRepository;
import net.elytrarace.common.ecs.Entity;
import net.elytrarace.server.ecs.component.PlayerRefComponent;
import net.elytrarace.server.ecs.component.ScoreComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Default {@link GameResultPersistenceService} that writes one row per player
 * to the {@code game_results} table. Operations run on the common fork-join pool
 * via {@link CompletableFuture#runAsync(Runnable)}.
 */
public final class GameResultPersistenceServiceImpl implements GameResultPersistenceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameResultPersistenceServiceImpl.class);

    private final ElytraPlayerRepository playerRepository;
    private final GameResultRepository gameResultRepository;

    public GameResultPersistenceServiceImpl(ElytraPlayerRepository playerRepository,
                                            GameResultRepository gameResultRepository) {
        this.playerRepository = Objects.requireNonNull(playerRepository, "playerRepository must not be null");
        this.gameResultRepository = Objects.requireNonNull(gameResultRepository, "gameResultRepository must not be null");
    }

    @Override
    public CompletableFuture<Void> persistResults(String cupName, String mapName, List<Entity> rankedPlayers) {
        Objects.requireNonNull(cupName, "cupName must not be null");
        Objects.requireNonNull(mapName, "mapName must not be null");
        Objects.requireNonNull(rankedPlayers, "rankedPlayers must not be null");

        if (rankedPlayers.isEmpty()) {
            LOGGER.debug("No ranked players — skipping persistence of game results");
            return CompletableFuture.completedFuture(null);
        }

        LocalDateTime playedAt = LocalDateTime.now();
        CompletableFuture<?>[] writes = new CompletableFuture<?>[rankedPlayers.size()];

        for (int i = 0; i < rankedPlayers.size(); i++) {
            Entity entity = rankedPlayers.get(i);
            int placement = i + 1;
            writes[i] = persistForPlayer(cupName, mapName, placement, entity, playedAt);
        }

        return CompletableFuture.allOf(writes)
                // Never propagate — the race must never crash because persistence failed.
                .exceptionally(ex -> {
                    LOGGER.error("One or more game result writes failed", ex);
                    return null;
                });
    }

    private CompletableFuture<Void> persistForPlayer(String cupName,
                                                     String mapName,
                                                     int placement,
                                                     Entity entity,
                                                     LocalDateTime playedAt) {
        if (!entity.hasComponent(PlayerRefComponent.class) || !entity.hasComponent(ScoreComponent.class)) {
            LOGGER.warn("Ranked entity missing PlayerRef or Score — skipping persistence");
            return CompletableFuture.completedFuture(null);
        }

        PlayerRefComponent ref = entity.getComponent(PlayerRefComponent.class);
        ScoreComponent score = entity.getComponent(ScoreComponent.class);
        UUID playerId = ref.getPlayerId();
        String username = ref.getPlayer().getUsername();
        int ringPoints = score.getRingPoints();
        int positionBonus = score.getPositionBonus();
        int totalPoints = score.getTotal();

        return playerRepository.getElytraPlayerById(playerId)
                .thenCompose(existing -> {
                    ElytraPlayerEntity playerEntity = existing != null
                            ? existing
                            : new ElytraPlayerEntity(playerId);
                    playerEntity.setLastKnownName(username);
                    playerEntity.setLastPlayed(playedAt);
                    playerEntity.setTotalGamesPlayed(playerEntity.getTotalGamesPlayed() + 1);
                    if (placement == 1) {
                        playerEntity.setTotalWins(playerEntity.getTotalWins() + 1);
                    }
                    playerEntity.setTotalRingsPassed(playerEntity.getTotalRingsPassed()
                            + computeRingsPassed(ringPoints));

                    // Ensure the ElytraPlayer row exists / is up to date before we insert the result
                    // to satisfy the foreign key in game_results.player_id.
                    CompletableFuture<Void> upsert = existing != null
                            ? playerRepository.updateElytraPlayer(playerEntity)
                            : playerRepository.saveElytraPlayer(playerEntity);

                    return upsert.thenCompose(v -> {
                        GameResultEntity result = new GameResultEntity(
                                playerEntity, cupName, mapName,
                                ringPoints, positionBonus, totalPoints,
                                placement, playedAt);
                        return gameResultRepository.saveResult(result);
                    });
                })
                .exceptionally(ex -> {
                    LOGGER.error("Failed to persist game result for player {} ({}): {}",
                            username, playerId, ex.getMessage(), ex);
                    return null;
                });
    }

    /**
     * Rings passed is not tracked on the ECS score directly — the ring point total
     * is its best proxy (1 ring = 1 point in the baseline scoring scheme).
     */
    private static int computeRingsPassed(int ringPoints) {
        return Math.max(0, ringPoints);
    }
}
