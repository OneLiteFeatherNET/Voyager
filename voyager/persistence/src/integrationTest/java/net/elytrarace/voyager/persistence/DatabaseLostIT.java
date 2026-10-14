package net.elytrarace.voyager.persistence;

import net.elytrarace.voyager.api.config.DatabaseConfig;
import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.race.MedalTier;
import net.elytrarace.voyager.persistence.schema.StatsDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static net.elytrarace.voyager.persistence.CompletionFixtures.START;
import static net.elytrarace.voyager.persistence.CompletionFixtures.finisher;
import static net.elytrarace.voyager.persistence.CompletionFixtures.player;
import static net.elytrarace.voyager.persistence.CompletionFixtures.race;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The database goes away during a race and comes back (design D5, spec "A lost database is retried with backoff").
 * Each test owns its container, so a stopped database never leaks into the next test. The backoff is driven by the
 * test's clock; nothing sleeps.
 */
@DisplayName("Statistics while the database is down")
@Testcontainers(disabledWithoutDocker = true)
class DatabaseLostIT extends DockerRequiredIT {

    private static final String MAP = "hangar";
    private static final String USER = "test";
    /** Unique to this class, so a check that the password is not printed cannot match any other text. */
    private static final String PASSWORD = "outage-probe-password";

    @Container
    private final PostgreSQLContainer postgres = ContainerOutage.postgres(PASSWORD);

    @Test
    @DisplayName("a completion is kept while the database is down and written after it returns")
    void aCompletionIsKeptWhileTheDatabaseIsDownAndWrittenAfterItReturns() throws SQLException {
        String url = FreshDatabase.create(postgres);
        ManualClock clock = new ManualClock(START);
        RecordingStatsLog log = new RecordingStatsLog();
        try (StatsDatabase database = StatsDatabase.open(url, USER, PASSWORD, 2, false)) {
            StatsWriter writer = new StatsWriter(new PostgresCompletionStore(database.sessionFactory()), clock,
                    new ExponentialBackoff(Duration.ofSeconds(1), Duration.ofSeconds(60)), log, 256);
            ContainerOutage.stop(postgres);

            writer.recordRace(race(MAP, GameMode.RACE, 0,
                    finisher(player(1), "Alex", 1, 61_200, MedalTier.GOLD, 0, 0, 0L, List.of(9_000))));
            assertThat(writer.step()).as("the first attempt is made during the outage").isTrue();
            assertThat(writer.retried()).as("the failed attempt is kept for a retry").isEqualTo(1);
            assertThat(writer.queued()).as("the completion waits in the queue").isEqualTo(1);

            ContainerOutage.start(postgres, USER);
            for (int attempt = 0; writer.queued() > 0 && attempt < 5; attempt++) {
                // One minute is longer than any wait of the policy, so each attempt is due.
                clock.advance(Duration.ofMinutes(1));
                writer.step();
            }

            assertThat(writer.written()).as("the kept completion is written once the database returns").isEqualTo(1);
            assertThat(writer.queued()).as("nothing is left in the queue").isZero();
            try (Connection connection = database.dataSource().getConnection()) {
                assertThat(SqlProbe.count(connection, "voyager.race_session"))
                        .as("the race is in the database").isEqualTo(1);
                assertThat(SqlProbe.count(connection, "voyager.race_result"))
                        .as("its result is in the database").isEqualTo(1);
            }
            assertThat(log.warnings().stream().filter(line -> line.contains("unreachable")).count())
                    .as("the start of the outage is logged once").isEqualTo(1);
            assertThat(log.warnings().stream().filter(line -> line.contains("reachable again")).count())
                    .as("the end of the outage is logged once").isEqualTo(1);
            assertThat(log.errors()).as("an outage is not an error").isEmpty();
        }
    }

    @Test
    @DisplayName("an on-start against a stopped database runs without stats and starts writing after the database returns")
    void anOnStartAgainstAStoppedDatabaseRunsWithoutStatsAndStartsWritingAfterTheDatabaseReturns()
            throws SQLException {
        String url = FreshDatabase.create(postgres);
        DatabaseConfig config = DatabaseConfig.from(Map.of(
                DatabaseConfig.KEY_PERSISTENCE, "on",
                DatabaseConfig.KEY_URL, url,
                DatabaseConfig.KEY_USER, USER,
                DatabaseConfig.KEY_PASSWORD, PASSWORD,
                DatabaseConfig.KEY_POOL_SIZE, "2"));
        ContainerOutage.stop(postgres);
        RecordingStatsLog log = new RecordingStatsLog();

        StatsStoreOutcome atStart = StatsStoreFactory.open(config, false, log);

        assertThat(atStart).as("a start that cannot reach the database is reported, not thrown")
                .isInstanceOf(StatsStoreOutcome.Unavailable.class);
        StatsStoreOutcome.Unavailable unavailable = (StatsStoreOutcome.Unavailable) atStart;
        assertThat(unavailable.reason()).as("the reason never contains the password").doesNotContain(PASSWORD);
        assertThat(log.warnings()).as("exactly one warning names the outage").hasSize(1);
        assertThat(log.warnings().getFirst()).as("the warning never contains the password").doesNotContain(PASSWORD);
        assertThat(log.errors()).as("a database at start is a warning, not an error").isEmpty();

        ContainerOutage.start(postgres, USER);

        StatsStoreOutcome afterReturn = StatsStoreFactory.open(config, false, log);
        assertThat(afterReturn).as("the same configuration opens once the database returns")
                .isInstanceOf(StatsStoreOutcome.Available.class);
        StatsStoreOutcome.Available available = (StatsStoreOutcome.Available) afterReturn;
        try (StatsDatabase database = available.database()) {
            available.store().writeRace(race(MAP, GameMode.RACE, 0,
                    finisher(player(1), "Alex", 1, 61_200, MedalTier.GOLD, 0, 0, 0L, List.of(9_000))));
            try (Connection connection = database.dataSource().getConnection()) {
                assertThat(SqlProbe.count(connection, "voyager.race_session"))
                        .as("the store writes to the database after it returned").isEqualTo(1);
            }
        }
    }
}
