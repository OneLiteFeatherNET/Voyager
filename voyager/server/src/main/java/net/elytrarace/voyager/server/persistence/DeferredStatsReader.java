package net.elytrarace.voyager.server.persistence;

import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.stats.LeaderboardEntry;
import net.elytrarace.voyager.api.stats.PlayerProfile;
import net.elytrarace.voyager.api.stats.StatsReader;
import net.elytrarace.voyager.api.stats.exception.StatsUnavailableException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The read side the server holds from the start. While the database is not open, every read answers that statistics are
 * unavailable right now; once a reader is bound, the reads go to the database.
 */
final class DeferredStatsReader implements StatsReader {

    /** The answer of every read while the database is not open. */
    static final String UNAVAILABLE = "statistics are unavailable right now";

    private final AtomicReference<StatsReader> target = new AtomicReference<>();

    /**
     * Binds the database's reader. Called once, when the database opens.
     *
     * @param reader the open reader
     */
    void bind(StatsReader reader) {
        target.set(reader);
    }

    @Override
    public PlayerProfile profile(UUID playerId) {
        return current().profile(playerId);
    }

    @Override
    public Optional<UUID> findPlayer(String name) {
        return current().findPlayer(name);
    }

    @Override
    public List<LeaderboardEntry> topTimes(String mapKey, String layoutVersion, GameMode mode, int limit) {
        return current().topTimes(mapKey, layoutVersion, mode, limit);
    }

    private StatsReader current() {
        StatsReader reader = target.get();
        if (reader == null) {
            throw new StatsUnavailableException(UNAVAILABLE, null);
        }
        return reader;
    }
}
