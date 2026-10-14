package net.elytrarace.voyager.persistence;

import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.stats.LeaderboardEntry;
import net.elytrarace.voyager.api.stats.PlayerProfile;
import net.elytrarace.voyager.persistence.schema.StatsDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("The statistics reads on PostgreSQL 17")
@Testcontainers(disabledWithoutDocker = true)
class PostgresStatsReaderIT extends DockerRequiredIT {

    private static final String MAP = "hangar";
    private static final String OTHER_LAYOUT = "fedcba9876543210";
    private static final Instant T0 = Instant.parse("2026-10-14T10:00:00Z");
    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CAROL = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_IMAGE);

    @Test
    @DisplayName("an unknown player has a profile with zero counts")
    void anUnknownPlayerHasAProfileWithZeroCounts() throws SQLException {
        String url = FreshDatabase.create(POSTGRES);
        try (StatsDatabase database = open(url)) {
            PlayerProfile profile = new PostgresStatsReader(database.sessionFactory()).profile(ALEX);

            assertThat(profile.lastKnownName()).as("no player row, so no name is known").isNull();
            assertThat(profile.cupsPlayed()).as("cups played").isZero();
            assertThat(profile.cupsWon()).as("cups won").isZero();
            assertThat(profile.diamonds() + profile.golds() + profile.silvers() + profile.bronzes())
                    .as("medals").isZero();
            assertThat(profile.flightMillis()).as("flight time").isZero();
            assertThat(profile.personalBests()).as("personal bests").isEmpty();
        }
    }

    @Test
    @DisplayName("the statistics row and the ranked medals are read")
    void theStatisticsRowAndTheRankedMedalsAreRead() throws SQLException {
        String url = FreshDatabase.create(POSTGRES);
        try (StatsDatabase database = open(url); Connection connection = database.dataSource().getConnection()) {
            StatsSeed.player(connection, ALEX, "Alex");
            StatsSeed.statistics(connection, ALEX, 3, 1, 4_200L);
            StatsSeed.raceResult(connection, ALEX, MAP, "RACE", "DIAMOND");
            StatsSeed.raceResult(connection, ALEX, MAP, "RACE", "GOLD");
            StatsSeed.raceResult(connection, ALEX, MAP, "RACE", "FINISH");
            StatsSeed.raceResult(connection, ALEX, MAP, "PRACTICE", "DIAMOND");

            PlayerProfile profile = new PostgresStatsReader(database.sessionFactory()).profile(ALEX);

            assertThat(profile.lastKnownName()).as("the last known name").isEqualTo("Alex");
            assertThat(profile.cupsPlayed()).as("cups played").isEqualTo(3);
            assertThat(profile.cupsWon()).as("cups won").isEqualTo(1);
            assertThat(profile.flightMillis()).as("total flight time").isEqualTo(4_200L);
            assertThat(profile.diamonds()).as("the practice diamond is not counted").isEqualTo(1);
            assertThat(profile.golds()).as("gold medals").isEqualTo(1);
            assertThat(profile.silvers()).as("silver medals").isZero();
            assertThat(profile.bronzes()).as("bronze medals").isZero();
        }
    }

    @Test
    @DisplayName("the personal bests of ranked modes are listed and practice bests are not")
    void thePersonalBestsOfRankedModesAreListedAndPracticeBestsAreNot() throws SQLException {
        String url = FreshDatabase.create(POSTGRES);
        try (StatsDatabase database = open(url); Connection connection = database.dataSource().getConnection()) {
            StatsSeed.player(connection, ALEX, "Alex");
            long race = StatsSeed.raceResult(connection, ALEX, MAP, "RACE", "FINISH");
            long practice = StatsSeed.raceResult(connection, ALEX, "orbit", "PRACTICE", "FINISH");
            StatsSeed.personalBest(connection, MAP, StatsSeed.LAYOUT_VERSION, "RACE", ALEX, 61_200, race, T0);
            StatsSeed.personalBest(connection, "orbit", StatsSeed.LAYOUT_VERSION, "PRACTICE", ALEX, 50_000, practice,
                    T0);

            PlayerProfile profile = new PostgresStatsReader(database.sessionFactory()).profile(ALEX);

            assertThat(profile.personalBests())
                    .as("only the ranked personal best is listed")
                    .singleElement()
                    .satisfies(best -> {
                        assertThat(best.mapKey()).isEqualTo(MAP);
                        assertThat(best.layoutVersion()).isEqualTo(StatsSeed.LAYOUT_VERSION);
                        assertThat(best.mode()).isEqualTo(GameMode.RACE);
                        assertThat(best.bestTimeMillis()).isEqualTo(61_200);
                        assertThat(best.achievedAt()).isEqualTo(T0);
                    });
        }
    }

    @Test
    @DisplayName("findPlayer matches the name case-insensitively and prefers the most recent player")
    void findPlayerMatchesTheNameCaseInsensitivelyAndPrefersTheMostRecentPlayer() throws SQLException {
        String url = FreshDatabase.create(POSTGRES);
        try (StatsDatabase database = open(url); Connection connection = database.dataSource().getConnection()) {
            StatsSeed.player(connection, ALEX, "Alex", T0.minus(1, ChronoUnit.DAYS));
            StatsSeed.player(connection, BOB, "alex", T0);
            StatsSeed.player(connection, CAROL, "Carol", T0.plus(1, ChronoUnit.DAYS));

            Optional<UUID> found = new PostgresStatsReader(database.sessionFactory()).findPlayer("ALEX");

            assertThat(found).as("the most recently seen player with that name").contains(BOB);
        }
    }

    @Test
    @DisplayName("findPlayer for a name nobody has is empty")
    void findPlayerForAnUnknownNameIsEmpty() throws SQLException {
        String url = FreshDatabase.create(POSTGRES);
        try (StatsDatabase database = open(url); Connection connection = database.dataSource().getConnection()) {
            StatsSeed.player(connection, ALEX, "Alex");

            Optional<UUID> found = new PostgresStatsReader(database.sessionFactory()).findPlayer("Nobody");

            assertThat(found).as("no player has that name").isEmpty();
        }
    }

    @Test
    @DisplayName("topTimes lists the fastest first and breaks a tie by the earlier finish")
    void topTimesListsTheFastestFirstAndBreaksATieByTheEarlierFinish() throws SQLException {
        String url = FreshDatabase.create(POSTGRES);
        try (StatsDatabase database = open(url); Connection connection = database.dataSource().getConnection()) {
            seedBest(connection, ALEX, "Alex", 61_200, T0.plusSeconds(100));
            seedBest(connection, BOB, "Bob", 61_200, T0);
            seedBest(connection, CAROL, "Carol", 59_000, T0.plusSeconds(200));

            List<LeaderboardEntry> entries = new PostgresStatsReader(database.sessionFactory())
                    .topTimes(MAP, StatsSeed.LAYOUT_VERSION, GameMode.RACE, 10);

            assertThat(entries).extracting(LeaderboardEntry::name)
                    .as("Carol is fastest; Bob and Alex tie, and Bob finished first")
                    .containsExactly("Carol", "Bob", "Alex");
            assertThat(entries).extracting(LeaderboardEntry::rank).containsExactly(1, 2, 3);
            assertThat(entries.get(1).achievedAt()).as("the tie keeps Bob's earlier finish").isEqualTo(T0);
        }
    }

    @Test
    @DisplayName("topTimes lists only the requested layout")
    void topTimesListsOnlyTheRequestedLayout() throws SQLException {
        String url = FreshDatabase.create(POSTGRES);
        try (StatsDatabase database = open(url); Connection connection = database.dataSource().getConnection()) {
            seedBest(connection, ALEX, "Alex", 61_200, T0);
            StatsSeed.player(connection, BOB, "Bob");
            long otherLayout = StatsSeed.raceResult(connection, BOB, MAP);
            StatsSeed.personalBest(connection, MAP, OTHER_LAYOUT, "RACE", BOB, 50_000, otherLayout, T0);

            List<LeaderboardEntry> entries = new PostgresStatsReader(database.sessionFactory())
                    .topTimes(MAP, StatsSeed.LAYOUT_VERSION, GameMode.RACE, 10);

            assertThat(entries).extracting(LeaderboardEntry::name)
                    .as("the old or other layout is not on the current leaderboard")
                    .containsExactly("Alex");
        }
    }

    @Test
    @DisplayName("topTimes stops at the limit")
    void topTimesStopsAtTheLimit() throws SQLException {
        String url = FreshDatabase.create(POSTGRES);
        try (StatsDatabase database = open(url); Connection connection = database.dataSource().getConnection()) {
            seedBest(connection, ALEX, "Alex", 61_200, T0);
            seedBest(connection, BOB, "Bob", 62_000, T0);
            seedBest(connection, CAROL, "Carol", 63_000, T0);

            List<LeaderboardEntry> entries = new PostgresStatsReader(database.sessionFactory())
                    .topTimes(MAP, StatsSeed.LAYOUT_VERSION, GameMode.RACE, 2);

            assertThat(entries).as("two entries, the two fastest").extracting(LeaderboardEntry::name)
                    .containsExactly("Alex", "Bob");
        }
    }

    @Test
    @DisplayName("topTimes of a practice mode is empty")
    void topTimesOfAPracticeModeIsEmpty() throws SQLException {
        String url = FreshDatabase.create(POSTGRES);
        try (StatsDatabase database = open(url); Connection connection = database.dataSource().getConnection()) {
            StatsSeed.player(connection, ALEX, "Alex");
            long practice = StatsSeed.raceResult(connection, ALEX, MAP, "PRACTICE", "FINISH");
            StatsSeed.personalBest(connection, MAP, StatsSeed.LAYOUT_VERSION, "PRACTICE", ALEX, 50_000, practice, T0);

            List<LeaderboardEntry> entries = new PostgresStatsReader(database.sessionFactory())
                    .topTimes(MAP, StatsSeed.LAYOUT_VERSION, GameMode.PRACTICE, 10);

            assertThat(entries).as("practice runs never appear on a leaderboard").isEmpty();
        }
    }

    private static StatsDatabase open(String url) {
        return StatsDatabase.open(url, POSTGRES.getUsername(), POSTGRES.getPassword(), 2, false);
    }

    /** One player with a ranked personal best on {@link #MAP} and the current layout. */
    private static void seedBest(Connection connection, UUID player, String name, int bestTimeMillis,
            Instant achievedAt) throws SQLException {
        StatsSeed.player(connection, player, name);
        long result = StatsSeed.raceResult(connection, player, MAP);
        StatsSeed.personalBest(connection, MAP, StatsSeed.LAYOUT_VERSION, "RACE", player, bestTimeMillis, result,
                achievedAt);
    }
}
