package net.elytrarace.voyager.platform.stats;

import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.stats.LeaderboardEntry;
import net.elytrarace.voyager.api.stats.PlayerProfile;
import net.elytrarace.voyager.api.stats.StatsReader;
import net.elytrarace.voyager.api.stats.exception.StatsUnavailableException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The reader of the {@code off} mode: there is nothing to read, so every read says so. The server asks its binding for the
 * mode before it answers a command, so {@code /stats} can say "not recorded" rather than "unavailable".
 */
public final class NoOpStatsReader implements StatsReader {

    /** The answer every read gives in this mode. */
    public static final String NOT_RECORDED = "statistics are not recorded on this server";

    @Override
    public PlayerProfile profile(UUID playerId) {
        throw new StatsUnavailableException(NOT_RECORDED, null);
    }

    @Override
    public Optional<UUID> findPlayer(String name) {
        throw new StatsUnavailableException(NOT_RECORDED, null);
    }

    @Override
    public List<LeaderboardEntry> topTimes(String mapKey, String layoutVersion, GameMode mode, int limit) {
        throw new StatsUnavailableException(NOT_RECORDED, null);
    }
}
