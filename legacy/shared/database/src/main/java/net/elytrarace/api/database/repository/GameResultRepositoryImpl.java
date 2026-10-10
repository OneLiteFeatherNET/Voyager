package net.elytrarace.api.database.repository;

import net.elytrarace.api.database.entity.GameResultEntity;
import org.hibernate.SessionFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class GameResultRepositoryImpl implements GameResultRepository {

    private final SessionFactory sessionFactory;

    public GameResultRepositoryImpl(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public CompletableFuture<Void> saveResult(GameResultEntity result) {
        return CompletableFuture.runAsync(() -> this.sessionFactory.inTransaction(session -> session.persist(result)));
    }

    @Override
    public CompletableFuture<List<GameResultEntity>> getResultsByPlayer(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> this.sessionFactory.fromSession(session ->
                session.createQuery(
                        "FROM GameResultEntity g WHERE g.player.playerId = :playerId ORDER BY g.playedAt DESC",
                        GameResultEntity.class
                ).setParameter("playerId", playerId).getResultList()
        ));
    }

    @Override
    public CompletableFuture<List<GameResultEntity>> getTopScores(String mapName, int limit) {
        return CompletableFuture.supplyAsync(() -> this.sessionFactory.fromSession(session ->
                session.createQuery(
                        "FROM GameResultEntity g WHERE g.mapName = :mapName ORDER BY g.totalPoints DESC",
                        GameResultEntity.class
                ).setParameter("mapName", mapName).setMaxResults(limit).getResultList()
        ));
    }
}
