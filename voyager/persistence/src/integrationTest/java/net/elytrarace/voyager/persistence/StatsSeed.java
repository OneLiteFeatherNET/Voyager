package net.elytrarace.voyager.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Inserts the rows that a personal best, a statistics row or a leaderboard row points at, so that each test seeds its
 * own data in its own database (FreshDatabase) and never relies on rows another test wrote. Timestamps are passed in
 * explicitly, so no seeded value depends on the wall clock.
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
        player(connection, playerId, name, Instant.parse("2026-10-14T10:00:00Z"));
    }

    /**
     * Inserts a player with an explicit last-seen instant.
     *
     * @param connection the connection of the test's database
     * @param playerId   the player's UUID
     * @param name       the last known name, at most sixteen characters
     * @param lastSeen   when the player was last seen
     * @throws SQLException when the insert fails
     */
    static void player(Connection connection, UUID playerId, String name, Instant lastSeen) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO voyager.player (player_id, last_known_name, first_seen, last_seen) "
                        + "VALUES (?, ?, ?, ?)")) {
            statement.setObject(1, playerId);
            statement.setString(2, name);
            statement.setObject(3, offset(lastSeen));
            statement.setObject(4, offset(lastSeen));
            statement.executeUpdate();
        }
    }

    /**
     * Inserts the running totals of a player, as the writer keeps them for ranked play.
     *
     * @param connection    the connection of the test's database
     * @param playerId      the player; the player row must exist
     * @param cupsPlayed    cups the player took part in
     * @param cupsWon       cups the player won
     * @param flightMillis  total gliding time in milliseconds
     * @throws SQLException when the insert fails
     */
    static void statistics(Connection connection, UUID playerId, long cupsPlayed, long cupsWon, long flightMillis)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO voyager.player_statistics (player_id, cups_played, cups_won, flight_millis) "
                        + "VALUES (?, ?, ?, ?)")) {
            statement.setObject(1, playerId);
            statement.setLong(2, cupsPlayed);
            statement.setLong(3, cupsWon);
            statement.setLong(4, flightMillis);
            statement.executeUpdate();
        }
    }

    /**
     * Inserts a ranked race session on {@code mapKey} and one finished result of {@code playerId} in it, without a
     * medal.
     *
     * @param connection the connection of the test's database
     * @param playerId   the player who owns the result; the player row must exist
     * @param mapKey     the map the session belongs to
     * @return the {@code race_result_id} of the inserted result
     * @throws SQLException when an insert fails
     */
    static long raceResult(Connection connection, UUID playerId, String mapKey) throws SQLException {
        return raceResult(connection, playerId, mapKey, "RACE", "FINISH");
    }

    /**
     * Inserts a race session in {@code gameMode} on {@code mapKey} and one result of {@code playerId} with the given
     * medal tier in it.
     *
     * @param connection the connection of the test's database
     * @param playerId   the player who owns the result; the player row must exist
     * @param mapKey     the map the session belongs to
     * @param gameMode   {@code RACE} or {@code PRACTICE}
     * @param medalTier  {@code DIAMOND}, {@code GOLD}, {@code SILVER}, {@code BRONZE}, {@code FINISH} or {@code DNF}
     * @return the {@code race_result_id} of the inserted result
     * @throws SQLException when an insert fails
     */
    static long raceResult(Connection connection, UUID playerId, String mapKey, String gameMode, String medalTier)
            throws SQLException {
        long sessionId = insertReturningId(connection,
                "INSERT INTO voyager.race_session (map_key, layout_version, game_mode, started_at, finished_at, "
                        + "human_count, bot_count) VALUES (?, ?, ?, now(), now(), 2, 0) RETURNING race_session_id",
                mapKey, LAYOUT_VERSION, gameMode);
        return insertReturningId(connection,
                "INSERT INTO voyager.race_result (race_session_id, player_id, ring_points, placement_bonus, "
                        + "medal_points, total_points, rings_passed, boosts_used, resets, flight_millis, medal_tier, "
                        + "validation_state) VALUES (?, ?, 0, 0, 0, 0, 0, 0, 0, 0, ?, 'VALID') "
                        + "RETURNING race_result_id",
                sessionId, playerId, medalTier);
    }

    /**
     * Inserts one personal best row.
     *
     * @param connection     the connection of the test's database
     * @param mapKey         the map's name
     * @param layoutVersion  the layout identity
     * @param gameMode       {@code RACE} or {@code PRACTICE}
     * @param playerId       the player; the player row must exist
     * @param bestTimeMillis the finish time
     * @param raceResultId   the race result the time belongs to; the row must exist
     * @param achievedAt     when the finish happened
     * @throws SQLException when the insert fails
     */
    static void personalBest(Connection connection, String mapKey, String layoutVersion, String gameMode,
            UUID playerId, int bestTimeMillis, long raceResultId, Instant achievedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO voyager.map_personal_best (map_key, layout_version, game_mode, player_id, "
                        + "best_time_millis, race_result_id, achieved_at) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, mapKey);
            statement.setString(2, layoutVersion);
            statement.setString(3, gameMode);
            statement.setObject(4, playerId);
            statement.setInt(5, bestTimeMillis);
            statement.setLong(6, raceResultId);
            statement.setObject(7, offset(achievedAt));
            statement.executeUpdate();
        }
    }

    private static OffsetDateTime offset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
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
