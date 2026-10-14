package net.elytrarace.voyager.persistence;

import net.elytrarace.voyager.persistence.schema.StatsDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("The leaderboard plan on PostgreSQL 17")
@Testcontainers(disabledWithoutDocker = true)
class LeaderboardPlanIT extends DockerRequiredIT {

    private static final String MAP = "hangar";
    private static final String OTHER_MAP = "orbit";
    /** At least two thousand rows for the map, so that the index, not the table size, decides the plan. */
    private static final int PLAYERS = 2000;
    private static final int LIMIT = 10;
    private static final String INDEX = "map_personal_best_leaderboard_idx";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_IMAGE);

    @Test
    @DisplayName("the leaderboard uses its index and has no sort node")
    void theLeaderboardUsesItsIndexAndHasNoSortNode() throws SQLException {
        String url = FreshDatabase.create(POSTGRES);
        try (StatsDatabase database =
                     StatsDatabase.open(url, POSTGRES.getUsername(), POSTGRES.getPassword(), 2, false);
             Connection connection = database.dataSource().getConnection()) {
            seed(connection);
            analyze(connection);

            String plan = explainLeaderboard(connection);

            assertThat(plan)
                    .as("no node of the plan sorts the rows: the index already delivers them fastest first")
                    .doesNotContainPattern("\"Node Type\": \"[^\"]*Sort[^\"]*\"");
            assertThat(plan)
                    .as("the leaderboard index is read by an Index Scan node")
                    .containsPattern("\"Node Type\": \"Index Scan\"[^{}]*\"Index Name\": \"" + INDEX + "\"");
        }
    }

    /**
     * Seeds {@value #PLAYERS} players, two ranked sessions and one personal best per player and session. The other
     * map's rows make the filter selective, as they are in production.
     */
    private static void seed(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO voyager.player (player_id, last_known_name, first_seen, last_seen) "
                            + "SELECT ('00000000-0000-0000-0000-' || lpad(g::text, 12, '0'))::uuid, 'racer' || g, "
                            + "now(), now() FROM generate_series(1, " + PLAYERS + ") g");
        }
        long mapSession = raceSession(connection, MAP);
        long otherSession = raceSession(connection, OTHER_MAP);
        for (long session : new long[] {mapSession, otherSession}) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO voyager.race_result (race_session_id, player_id, ring_points, placement_bonus, "
                            + "medal_points, total_points, rings_passed, boosts_used, resets, flight_millis, "
                            + "medal_tier, validation_state) "
                            + "SELECT ?, player_id, 0, 0, 0, 0, 0, 0, 0, 0, 'FINISH', 'VALID' FROM voyager.player")) {
                statement.setLong(1, session);
                statement.executeUpdate();
            }
        }
        insertPersonalBests(connection, MAP, mapSession);
        insertPersonalBests(connection, OTHER_MAP, otherSession);
    }

    private static long raceSession(Connection connection, String mapKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO voyager.race_session (map_key, layout_version, game_mode, started_at, finished_at, "
                        + "human_count, bot_count) VALUES (?, ?, 'RACE', now(), now(), 2, 0) "
                        + "RETURNING race_session_id")) {
            statement.setString(1, mapKey);
            statement.setString(2, StatsSeed.LAYOUT_VERSION);
            try (ResultSet generated = statement.executeQuery()) {
                generated.next();
                return generated.getLong(1);
            }
        }
    }

    /** Times spread over 30 seconds from 60 seconds, so that many players share a second and ties occur. */
    private static void insertPersonalBests(Connection connection, String mapKey, long session) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO voyager.map_personal_best (map_key, layout_version, game_mode, player_id, "
                        + "best_time_millis, race_result_id, achieved_at) "
                        + "SELECT ?, ?, 'RACE', r.player_id, 60000 + (r.race_result_id * 7919) % 30000, "
                        + "r.race_result_id, now() FROM voyager.race_result r WHERE r.race_session_id = ?")) {
            statement.setString(1, mapKey);
            statement.setString(2, StatsSeed.LAYOUT_VERSION);
            statement.setLong(3, session);
            statement.executeUpdate();
        }
    }

    private static void analyze(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ANALYZE voyager.player");
            statement.execute("ANALYZE voyager.map_personal_best");
        }
    }

    /**
     * The plan of the reader's leaderboard statement, prepared with the parameter types the driver binds for Strings
     * ({@code varchar}) and executed with the values of the test map and the limit. The plan is the JSON text
     * PostgreSQL returns for {@code EXPLAIN (FORMAT JSON)}.
     */
    private static String explainLeaderboard(Connection connection) throws SQLException {
        String prepared = PostgresStatsReader.TOP_TIMES_SQL
                .replace(":mapKey", "$1")
                .replace(":layoutVersion", "$2")
                .replace(":gameMode", "$3")
                .replace(":limit", "$4");
        try (Statement statement = connection.createStatement()) {
            statement.execute("PREPARE leaderboard (varchar, varchar, varchar, integer) AS " + prepared);
            try (ResultSet plan = statement.executeQuery(
                    "EXPLAIN (FORMAT JSON) EXECUTE leaderboard('%s', '%s', 'RACE', %d)"
                            .formatted(MAP, StatsSeed.LAYOUT_VERSION, LIMIT))) {
                assertThat(plan.next()).as("EXPLAIN returns one row").isTrue();
                return plan.getString(1);
            }
        }
    }
}
