package net.elytrarace.api.database.repository;

import org.hibernate.SessionFactory;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Persists and retrieves the all-time fastest completion time for each (cup, map).
 */
public interface MapRecordRepository {

    static MapRecordRepository createInstance(SessionFactory sessionFactory) {
        return new MapRecordRepositoryImpl(sessionFactory);
    }

    /**
     * Returns the current record time in milliseconds for the given (cup, map),
     * or an empty Optional if no record exists yet.
     */
    CompletableFuture<Optional<Long>> getRecordTime(String cupName, String mapName);

    /**
     * Saves a new record or updates the existing one if {@code timeMs} is faster.
     * Does nothing if the existing record is already equal to or faster than {@code timeMs}.
     */
    CompletableFuture<Void> saveOrUpdateRecord(String cupName, String mapName,
                                               UUID holderId, long timeMs);
}
