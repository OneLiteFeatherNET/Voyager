package net.elytrarace.voyager.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Inserts the parent rows that a personal best or a leaderboard row points at, so that each test seeds its own data in
 * its own database (FreshDatabase) and never relies on rows another test wrote.
 */
final class StatsSeed {

    /** The layout identity every seeded race session carries; 16 lowercase hex characters, as design D7 requires. */
    static final String LAYOUT_VERSION = "0123456789abcdef";

    private StatsSeed() {
    }

    /**
     * Inserts a player whose first and last seen are now.
     *
     * @param connection the connection of the test's database
     * @param playerId   the player's UUID
     * @param name       the last known name, at most sixteen characters
     * @throws SQLException when the insert fails
     */
    static void player(Connection connection, UUID playerId, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO voyager.player (player_id, last_known_name, first_seen, last_seen) "
                        + "VALUES (?, ?, now(), now())")) {
            statement.setObject(1, playerId);
            statement.setString(2, name);
            statement.executeUpdate();
        }
    }

    /**
     * Inserts a race session on map {@code map} in ranked mode and one result of {@code playerId} in it.
     *
     * @param connection the connection of the test's database
     * @param playerId   the player who owns the result; the player row must exist
     * @param mapKey     the map the session belongs to
     * @return the {@code race_result_id} of the inserted result
     * @throws SQLException when an insert fails
     */
    static long raceResult(Connection connection, UUID playerId, String mapKey) throws SQLException {
        long sessionId = insertReturningId(connection,
                "INSERT INTO voyager.race_session (map_key, layout_version, game_mode, started_at, finished_at, "
                        + "human_count, bot_count) VALUES (?, ?, 'RACE', now(), now(), 2, 0) RETURNING race_session_id",
                mapKey, LAYOUT_VERSION);
        return insertReturningId(connection,
                "INSERT INTO voyager.race_result (race_session_id, player_id, ring_points, placement_bonus, "
                        + "medal_points, total_points, rings_passed, boosts_used, resets, flight_millis, medal_tier, "
                        + "validation_state) VALUES (?, ?, 0, 0, 0, 0, 0, 0, 0, 0, 'FINISH', 'VALID') "
                        + "RETURNING race_result_id",
                sessionId, playerId);
    }

    private static long insertReturningId(Connection connection, String sql, Object... parameters)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet generated = statement.executeQuery()) {
                generated.next();
                return generated.getLong(1);
            }
        }
    }
}
