package net.elytrarace.voyager.persistence;

import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.persistence.PersonalBestUpsert.Candidate;
import net.elytrarace.voyager.persistence.schema.StatsDatabase;
import org.hibernate.StatelessSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("The personal best upsert on a tie")
@Testcontainers(disabledWithoutDocker = true)
class PersonalBestUpsertIT extends DockerRequiredIT {

    private static final String MAP = "hangar";
    private static final Instant FIRST_FINISH = Instant.parse("2026-10-14T10:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_IMAGE);

    @Test
    @DisplayName("an equal time keeps the earlier finish")
    void anEqualTimeKeepsTheEarlierFinish() throws SQLException {
        String url = FreshDatabase.create(POSTGRES);
        UUID player = UUID.randomUUID();
        try (StatsDatabase database =
                     StatsDatabase.open(url, POSTGRES.getUsername(), POSTGRES.getPassword(), 2, false)) {
            long laterRun;
            long earlierRun;
            try (Connection connection = database.dataSource().getConnection()) {
                StatsSeed.player(connection, player, "Alex");
                laterRun = StatsSeed.raceResult(connection, player, MAP);
                earlierRun = StatsSeed.raceResult(connection, player, MAP);
            }
            int tie = 61_200;

            // The later finish is written first, so the earlier finish arrives second with the same time.
            upsert(database, new Candidate(MAP, StatsSeed.LAYOUT_VERSION, GameMode.RACE, player, tie, laterRun,
                    FIRST_FINISH.plusSeconds(100)));
            upsert(database, new Candidate(MAP, StatsSeed.LAYOUT_VERSION, GameMode.RACE, player, tie, earlierRun,
                    FIRST_FINISH));

            try (Connection connection = database.dataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT best_time_millis, race_result_id, achieved_at FROM voyager.map_personal_best "
                                 + "WHERE player_id = ?")) {
                statement.setObject(1, player);
                try (ResultSet row = statement.executeQuery()) {
                    assertThat(row.next()).as("the player has one personal best row").isTrue();
                    assertThat(row.getLong(2))
                            .as("the earlier finish keeps the row, so its race result is stored")
                            .isEqualTo(earlierRun);
                    assertThat(row.getObject(3, OffsetDateTime.class).toInstant())
                            .as("the stored finish instant is the earlier one")
                            .isEqualTo(FIRST_FINISH);
                    assertThat(row.getInt(1)).as("the time itself is the tie value").isEqualTo(tie);
                }
            }
        }
    }

    private static void upsert(StatsDatabase database, Candidate candidate) {
        try (StatelessSession session = database.sessionFactory().openStatelessSession()) {
            session.beginTransaction();
            PersonalBestUpsert.upsert(session, candidate);
            session.getTransaction().commit();
        }
    }
}
