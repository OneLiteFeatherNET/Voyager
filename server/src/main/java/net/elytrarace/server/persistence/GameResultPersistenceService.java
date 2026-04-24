package net.elytrarace.server.persistence;

import net.elytrarace.common.ecs.Entity;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Persists the final standings of a completed race to the database.
 * <p>
 * Called by {@code MinestomEndPhase} after position bonuses are calculated.
 * Writes happen off the Minestom tick thread so a slow database cannot stall
 * the game loop. Failures are logged, never thrown — the race result is a
 * nice-to-have, never allowed to crash gameplay.
 */
public interface GameResultPersistenceService {

    /**
     * Persists one {@code GameResultEntity} per ranked player entity.
     *
     * @param cupName       name of the cup the race belonged to
     * @param mapName       name of the map that just finished
     * @param rankedPlayers player entities sorted by placement (index 0 = 1st place);
     *                      each must carry a {@code PlayerRefComponent} and {@code ScoreComponent}
     * @return a future that completes when all writes finish (normally or exceptionally handled);
     *         the future itself never completes exceptionally — errors are swallowed and logged
     */
    CompletableFuture<Void> persistResults(String cupName, String mapName, List<Entity> rankedPlayers);
}
