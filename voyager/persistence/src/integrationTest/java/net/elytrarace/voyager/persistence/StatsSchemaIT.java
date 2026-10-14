package net.elytrarace.voyager.persistence;

import net.elytrarace.voyager.persistence.schema.FlywayMigrator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("The statistics schema on PostgreSQL 17")
@Testcontainers(disabledWithoutDocker = true)
class StatsSchemaIT extends DockerRequiredIT {

    private static final String SCHEMA = "voyager";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_IMAGE);

    @Test
    @DisplayName("the baseline applies once in schema voyager")
    void theBaselineAppliesOnceInSchemaVoyager() throws SQLException {
        String url = FreshDatabase.create(POSTGRES);

        int applied = FlywayMigrator.migrate(url, POSTGRES.getUsername(), POSTGRES.getPassword());

        assertThat(applied).as("version 1 is the only migration and it runs on an empty database").isEqualTo(1);
        try (Connection connection = connect(url)) {
            assertThat(tableNames(connection))
                    .as("schema voyager holds the eight tables of design D4 and Flyway's history table")
                    .containsExactlyInAnyOrder(
                            "player", "player_statistics", "race_session", "race_result", "race_split",
                            "cup_session", "cup_result", "map_personal_best", "flyway_schema_history");
            assertThat(successfulVersions(connection))
                    .as("Flyway recorded version 1 as successful, once")
                    .containsExactly("1");
        }
    }

    @Test
    @DisplayName("a second start applies nothing")
    void aSecondStartAppliesNothing() throws SQLException {
        String url = FreshDatabase.create(POSTGRES);
        FlywayMigrator.migrate(url, POSTGRES.getUsername(), POSTGRES.getPassword());

        int secondStart = FlywayMigrator.migrate(url, POSTGRES.getUsername(), POSTGRES.getPassword());

        assertThat(secondStart).as("the database is already at version 1, so the second start applies nothing")
                .isZero();
    }

    private static Connection connect(String url) throws SQLException {
        return DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static List<String> tableNames(Connection connection) throws SQLException {
        return query(connection,
                "SELECT table_name FROM information_schema.tables WHERE table_schema = ? ORDER BY table_name",
                SCHEMA, rows -> rows.getString(1));
    }

    private static List<String> successfulVersions(Connection connection) throws SQLException {
        return query(connection,
                // The schema-creation row has no version; only the migrations carry one.
                "SELECT version FROM voyager.flyway_schema_history WHERE success AND version IS NOT NULL "
                        + "ORDER BY installed_rank",
                null, rows -> rows.getString(1));
    }

    private static List<String> query(Connection connection, String sql, String parameter, RowMapper mapper)
            throws SQLException {
        List<String> values = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (parameter != null) {
                statement.setString(1, parameter);
            }
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    values.add(mapper.map(rows));
                }
            }
        }
        return values;
    }

    @FunctionalInterface
    private interface RowMapper {
        String map(ResultSet rows) throws SQLException;
    }
}
