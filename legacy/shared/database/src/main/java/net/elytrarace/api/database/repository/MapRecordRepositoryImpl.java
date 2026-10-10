package net.elytrarace.api.database.repository;

import net.elytrarace.api.database.entity.MapRecordEntity;
import org.hibernate.SessionFactory;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class MapRecordRepositoryImpl implements MapRecordRepository {

    private final SessionFactory sessionFactory;

    MapRecordRepositoryImpl(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public CompletableFuture<Optional<Long>> getRecordTime(String cupName, String mapName) {
        return CompletableFuture.supplyAsync(() ->
                sessionFactory.fromSession(session ->
                        session.createQuery(
                                        "from MapRecordEntity where cupName = :cup and mapName = :map",
                                        MapRecordEntity.class)
                                .setParameter("cup", cupName)
                                .setParameter("map", mapName)
                                .uniqueResultOptional()
                                .map(MapRecordEntity::getRecordTimeMs)));
    }

    @Override
    public CompletableFuture<Void> saveOrUpdateRecord(String cupName, String mapName,
                                                      UUID holderId, long timeMs) {
        return CompletableFuture.runAsync(() ->
                sessionFactory.inTransaction(session -> {
                    MapRecordEntity existing = session.createQuery(
                                    "from MapRecordEntity where cupName = :cup and mapName = :map",
                                    MapRecordEntity.class)
                            .setParameter("cup", cupName)
                            .setParameter("map", mapName)
                            .uniqueResultOptional()
                            .orElse(null);

                    LocalDateTime now = LocalDateTime.now();
                    if (existing == null) {
                        session.persist(new MapRecordEntity(cupName, mapName, timeMs, holderId, now));
                    } else if (timeMs < existing.getRecordTimeMs()) {
                        existing.setRecordTimeMs(timeMs);
                        existing.setHolderPlayerId(holderId);
                        existing.setAchievedAt(now);
                        session.merge(existing);
                    }
                }));
    }
}
