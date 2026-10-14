package net.elytrarace.voyager.api.stats;

import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.stats.exception.StatsUnavailableException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads statistics for {@code /stats} and {@code /leaderboard}. Called off the game loop; a read that
 * fails or cannot be answered throws {@link StatsUnavailableException}.
 */
public interface StatsReader {

    /**
     * Whether statistics are kept on this server at all. {@code false} means every read will fail, and a command can say
     * so without asking: the switch is off, so nothing was or will be recorded.
     *
     * @return {@code true} unless the server keeps no statistics
     */
    default boolean recorded() {
        return true;
    }

    /**
     * The statistics of one player. A player with no recorded run gets a profile with zero counts.
     *
     * @param playerId the player's UUID
     * @return the player's profile
     * @throws StatsUnavailableException when the store cannot answer
     */
    PlayerProfile profile(UUID playerId);

    /**
     * Finds a player by last known name, matching case-insensitively. When several players match, the one
     * seen most recently wins.
     *
     * @param name the name typed by the player
     * @return the player's UUID, or empty when no player has that name
     * @throws StatsUnavailableException when the store cannot answer
     */
    Optional<UUID> findPlayer(String name);

    /**
     * The fastest personal bests on one map's current layout, ranked mode only.
     *
     * @param mapKey        the map's name in its file
     * @param layoutVersion the current layout identity of the map
     * @param mode          the game mode to list
     * @param limit         the number of entries, at least one
     * @return the entries, fastest first; empty when the map has no times yet
     * @throws StatsUnavailableException when the store cannot answer
     */
    List<LeaderboardEntry> topTimes(String mapKey, String layoutVersion, GameMode mode, int limit);
}
