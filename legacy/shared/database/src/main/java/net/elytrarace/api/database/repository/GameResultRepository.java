package net.elytrarace.api.database.repository;

import net.elytrarace.api.database.entity.GameResultEntity;
import org.hibernate.SessionFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface GameResultRepository {

    /**
     * Saves a game result to the database
     *
     * @param result The game result to save
     * @return A CompletableFuture that completes when the result is saved
     */
    CompletableFuture<Void> saveResult(GameResultEntity result);

    /**
     * Retrieves all game results for a specific player
     *
     * @param playerId The UUID of the player
     * @return A CompletableFuture containing the list of game results
     */
    CompletableFuture<List<GameResultEntity>> getResultsByPlayer(UUID playerId);

    /**
     * Retrieves the top scores for a specific map
     *
     * @param mapName The name of the map
     * @param limit   The maximum number of results to return
     * @return A CompletableFuture containing the list of top game results
     */
    CompletableFuture<List<GameResultEntity>> getTopScores(String mapName, int limit);

    static GameResultRepository createInstance(SessionFactory sessionFactory) {
        return new GameResultRepositoryImpl(sessionFactory);
    }
}
