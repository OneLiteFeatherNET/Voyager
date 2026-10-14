package net.elytrarace.voyager.persistence;

import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.persistence.PersonalBestUpsert.Candidate;
import net.elytrarace.voyager.persistence.schema.StatsDatabase;
import org.hibernate.SessionFactory;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("The personal best under two concurrent writers")
@Testcontainers(disabledWithoutDocker = true)
class PersonalBestConcurrencyIT extends DockerRequiredIT {

    private static final String MAP = "hangar";
    private static final int UPSERTS_PER_WRITER = 500;
    /** Both writers start from this instant, so the achieved-at values of their runs are fixed and distinct. */
    private static final Instant BASE = Instant.parse("2026-10-14T10:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_IMAGE);

    @Test
    @DisplayName("two writers keep the fastest time")
    void twoWritersKeepTheFastestTime() throws Exception {
        String url = FreshDatabase.create(POSTGRES);
        UUID player = UUID.randomUUID();
        try (StatsDatabase database =
                     StatsDatabase.open(url, POSTGRES.getUsername(), POSTGRES.getPassword(), 4, false)) {
            long writerARace;
            long writerBRace;
            try (Connection connection = database.dataSource().getConnection()) {
                StatsSeed.player(connection, player, "Alex");
                writerARace = StatsSeed.raceResult(connection, player, MAP);
                writerBRace = StatsSeed.raceResult(connection, player, MAP);
            }

            // Writer A's time falls from its first finish to its last, and its minimum is the first write.
            // Writer B is always slower than writer A's minimum. The expected value is therefore fixed,
            // whatever the interleaving; a last-writer-wins upsert ends on a later value of writer A or B.
            List<Candidate> writerA = finishes(player, writerARace, 70_000, 0);
            List<Candidate> writerB = finishes(player, writerBRace, 70_250, UPSERTS_PER_WRITER);

            runConcurrently(database.sessionFactory(), writerA, writerB);

            try (Connection connection = database.dataSource().getConnection()) {
                List<PersonalBestRow> rows = personalBests(connection, player);
                assertThat(rows).as("one player on one map has exactly one personal best row").hasSize(1);
                PersonalBestRow best = rows.getFirst();
                assertThat(best.bestTimeMillis())
                        .as("the fastest of the 1000 finishes is writer A's first run, 70000 ms")
                        .isEqualTo(70_000);
                assertThat(best.raceResultId())
                        .as("the race result of the run that set the fastest time")
                        .isEqualTo(writerARace);
                assertThat(best.achievedAt())
                        .as("the finish instant of the run that set the fastest time")
                        .isEqualTo(BASE);
            }
        }
    }

    /**
     * Builds one writer's sequence of {@value #UPSERTS_PER_WRITER} finishes. The times are {@code baseMillis} plus a
     * value below 500, and each finish carries its own instant, {@code achievedOffset} seconds after {@link #BASE}
     * plus its index.
     */
    private static List<Candidate> finishes(UUID player, long raceResultId, int baseMillis, int achievedOffset) {
        List<Candidate> finishes = new ArrayList<>(UPSERTS_PER_WRITER);
        for (int i = 0; i < UPSERTS_PER_WRITER; i++) {
            // 37 is coprime with 500, so the sequence visits many values, and it ends away from its minimum.
            int time = baseMillis + (i * 37) % 500;
            Instant achievedAt = BASE.plusSeconds(achievedOffset + i);
            finishes.add(new Candidate(MAP, StatsSeed.LAYOUT_VERSION, GameMode.RACE, player, time, raceResultId,
                    achievedAt));
        }
        return finishes;
    }

    /**
     * Runs both writers from one start signal. Each upsert is one transaction on its own stateless session, so the two
     * writers interleave row by row.
     */
    private static void runConcurrently(SessionFactory sessionFactory, List<Candidate> writerA, List<Candidate> writerB)
            throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> a = executor.submit(() -> write(sessionFactory, writerA, start));
            Future<?> b = executor.submit(() -> write(sessionFactory, writerB, start));
            start.countDown();
            a.get(60, TimeUnit.SECONDS);
            b.get(60, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void write(SessionFactory sessionFactory, List<Candidate> candidates, CountDownLatch start) {
        awaitStart(start);
        for (Candidate candidate : candidates) {
            try (StatelessSession session = sessionFactory.openStatelessSession()) {
                session.beginTransaction();
                PersonalBestUpsert.upsert(session, candidate);
                session.getTransaction().commit();
            }
        }
    }

    private static void awaitStart(CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("a writer was interrupted before the start signal", interrupted);
        }
    }

    private static List<PersonalBestRow> personalBests(Connection connection, UUID player) throws SQLException {
        List<PersonalBestRow> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT best_time_millis, race_result_id, achieved_at FROM voyager.map_personal_best "
                        + "WHERE player_id = ?")) {
            statement.setObject(1, player);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(new PersonalBestRow(
                            result.getInt(1),
                            result.getLong(2),
                            result.getObject(3, OffsetDateTime.class).toInstant()));
                }
            }
        }
        return rows;
    }

    private record PersonalBestRow(int bestTimeMillis, long raceResultId, Instant achievedAt) {
    }
}
