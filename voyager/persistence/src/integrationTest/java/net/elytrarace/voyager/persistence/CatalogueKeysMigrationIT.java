package net.elytrarace.voyager.persistence;

import com.zaxxer.hikari.HikariDataSource;
import net.elytrarace.voyager.persistence.schema.FlywayMigrator;
import net.elytrarace.voyager.persistence.schema.HikariDataSources;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The version 2 migration rewrites the statistics written under a flat id to the catalogue key (ADR-0040). The rows are
 * written at version 1, the way the server wrote them before this change, and the migration runs over them.
 */
@DisplayName("The catalogue key migration of the statistics schema")
@Testcontainers(disabledWithoutDocker = true)
class CatalogueKeysMigrationIT extends DockerRequiredIT {

    private static final String FLAT_BLUE_AND_RED = "elytraraceblueandred";
    private static final String FLAT_ALPHA_CUP = "alpha_cup";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_IMAGE);

    @Test
    @DisplayName("a race session written under a flat map id is read back under the map's key")
    void aRaceSessionUnderAFlatIdIsRewrittenToTheKey() throws SQLException {
        String url = writtenAtVersionOne(connection -> {
            UUID player = UUID.randomUUID();
            StatsSeed.player(connection, player, "racer");
            StatsSeed.raceResult(connection, player, FLAT_BLUE_AND_RED);
        });

        assertThat(strings(url, "SELECT map_key FROM voyager.race_session"))
                .containsExactly("elytrarace:blue_and_red");
    }

    @Test
    @DisplayName("a personal best and a cup session under their flat ids are rewritten to their keys")
    void aPersonalBestAndACupSessionAreRewrittenToTheirKeys() throws SQLException {
        String url = writtenAtVersionOne(connection -> {
            UUID player = UUID.randomUUID();
            StatsSeed.player(connection, player, "racer");
            long result = StatsSeed.raceResult(connection, player, FLAT_BLUE_AND_RED);
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO voyager.map_personal_best (map_key, layout_version, game_mode, player_id, "
                            + "best_time_millis, race_result_id, achieved_at) VALUES (?, ?, 'RACE', ?, 60000, ?, now())")) {
                insert.setString(1, FLAT_BLUE_AND_RED);
                insert.setString(2, StatsSeed.LAYOUT_VERSION);
                insert.setObject(3, player);
                insert.setLong(4, result);
                insert.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO voyager.cup_session (cup_key, game_mode, started_at, finished_at, racer_count) "
                            + "VALUES (?, 'RACE', now(), now(), 1)")) {
                insert.setString(1, FLAT_ALPHA_CUP);
                insert.executeUpdate();
            }
        });

        assertThat(strings(url, "SELECT map_key FROM voyager.map_personal_best"))
                .containsExactly("elytrarace:blue_and_red");
        assertThat(strings(url, "SELECT cup_key FROM voyager.cup_session"))
                .containsExactly("elytrarace:alpha_cup");
    }

    @Test
    @DisplayName("the layout version of a rewritten row is the one it was written with")
    void theLayoutVersionIsUnchanged() throws SQLException {
        String url = writtenAtVersionOne(connection -> {
            UUID player = UUID.randomUUID();
            StatsSeed.player(connection, player, "racer");
            StatsSeed.raceResult(connection, player, FLAT_BLUE_AND_RED);
        });

        assertThat(strings(url, "SELECT layout_version FROM voyager.race_session"))
                .containsExactly(StatsSeed.LAYOUT_VERSION);
    }

    @Test
    @DisplayName("a row for a map no longer in the catalogue is left as it was")
    void aRowForAMapNoLongerInTheCatalogueIsLeftAlone() throws SQLException {
        String url = writtenAtVersionOne(connection -> {
            UUID player = UUID.randomUUID();
            StatsSeed.player(connection, player, "racer");
            StatsSeed.raceResult(connection, player, "hangar");
        });

        assertThat(strings(url, "SELECT map_key FROM voyager.race_session")).containsExactly("hangar");
    }

    /**
     * Creates a fresh database, applies the schema up to version 1, lets {@code writer} write rows as version 1 holds
     * them, and then applies the rest of the migrations. Returns the database URL for the assertions.
     */
    private String writtenAtVersionOne(RowWriter writer) throws SQLException {
        String url = FreshDatabase.create(POSTGRES);
        try (HikariDataSource pool = pool(url)) {
            FlywayMigrator.migrateTo(pool, "1");
        }
        try (Connection connection = connect(url)) {
            writer.write(connection);
        }
        try (HikariDataSource pool = pool(url)) {
            FlywayMigrator.migrate(pool);
        }
        return url;
    }

    private interface RowWriter {
        void write(Connection connection) throws SQLException;
    }

    private static HikariDataSource pool(String url) {
        return HikariDataSources.create(url, POSTGRES.getUsername(), POSTGRES.getPassword(), 2, false);
    }

    private static Connection connect(String url) throws SQLException {
        return DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static List<String> strings(String url, String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Connection connection = connect(url);
             PreparedStatement query = connection.prepareStatement(sql);
             ResultSet rows = query.executeQuery()) {
            while (rows.next()) {
                values.add(rows.getString(1).strip());
            }
        }
        return values;
    }
}
