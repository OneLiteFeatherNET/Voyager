package net.elytrarace.voyager.persistence;

import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.race.MedalTier;
import net.elytrarace.voyager.api.stats.CupCompletion;
import net.elytrarace.voyager.api.stats.RaceCompletion;
import net.elytrarace.voyager.persistence.exception.StoreException;
import net.elytrarace.voyager.persistence.schema.StatsDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import static net.elytrarace.voyager.persistence.CompletionFixtures.cup;
import static net.elytrarace.voyager.persistence.CompletionFixtures.cupResult;
import static net.elytrarace.voyager.persistence.CompletionFixtures.dnf;
import static net.elytrarace.voyager.persistence.CompletionFixtures.finisher;
import static net.elytrarace.voyager.persistence.CompletionFixtures.player;
import static net.elytrarace.voyager.persistence.CompletionFixtures.race;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("The completion store on PostgreSQL 17")
@Testcontainers(disabledWithoutDocker = true)
class PostgresCompletionStoreIT extends DockerRequiredIT {

    private static final String MAP = "hangar";
    private static final String CUP = "spring";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_IMAGE);

    @Test
    @DisplayName("one finished race writes its session, its results, its splits and the personal bests in one transaction")
    void oneFinishedRaceWritesItsSessionResultsSplitsAndPersonalBestsInOneTransaction() throws SQLException {
        String url = FreshDatabase.create(POSTGRES);
        RaceCompletion completed = race(MAP, GameMode.RACE, 3,
                finisher(player(1), "Alex", 1, 61_200, MedalTier.GOLD, 2, 1, 4_000L, List.of(9_000, 18_000, 27_000)),
                dnf(player(2), "Bea", List.of(9_500, 19_000)));
        try (StatsDatabase database = open(url)) {
            new PostgresCompletionStore(database.sessionFactory()).writeRace(completed);

            try (Connection connection = database.dataSource().getConnection()) {
                assertThat(SqlProbe.rows(connection,
                        "SELECT map_key, game_mode, human_count, bot_count FROM voyager.race_session"))
                        .as("one session row for the map, with its racers and the bot count")
                        .containsExactly(List.of(MAP, "RACE", 2, 3));
                assertThat(SqlProbe.rows(connection,
                        "SELECT p.last_known_name, rr.finish_position::int, rr.finish_time_millis, rr.medal_tier, "
                                + "rr.rings_passed, rr.boosts_used, rr.resets, rr.flight_millis "
                                + "FROM voyager.race_result rr JOIN voyager.player p ON p.player_id = rr.player_id "
                                + "ORDER BY p.last_known_name"))
                        .as("one result per human racer; the racer who did not finish has no position or time")
                        .containsExactly(
                                List.of("Alex", 1, 61_200, "GOLD", 3, 2, 1, 4_000),
                                Arrays.asList("Bea", null, null, "DNF", 2, 0, 0, 0));
                assertThat(SqlProbe.rows(connection,
                        "SELECT p.last_known_name, s.ring_index, s.game_tick FROM voyager.race_split s "
                                + "JOIN voyager.race_result rr ON rr.race_result_id = s.race_result_id "
                                + "JOIN voyager.player p ON p.player_id = rr.player_id "
                                + "ORDER BY p.last_known_name, s.ring_index"))
                        .as("one split per ring passed, in ring order")
                        .containsExactly(
                                List.of("Alex", 0, 9_000),
                                List.of("Alex", 1, 18_000),
                                List.of("Alex", 2, 27_000),
                                List.of("Bea", 0, 9_500),
                                List.of("Bea", 1, 19_000));
                assertThat(SqlProbe.rows(connection,
                        "SELECT p.last_known_name, pb.best_time_millis, pb.game_mode, rr.medal_tier "
                                + "FROM voyager.map_personal_best pb "
                                + "JOIN voyager.player p ON p.player_id = pb.player_id "
                                + "JOIN voyager.race_result rr ON rr.race_result_id = pb.race_result_id"))
                        .as("the finisher's personal best points at the finisher's result")
                        .containsExactly(List.of("Alex", 61_200, "RACE", "GOLD"));
                assertThat(SqlProbe.rows(connection,
                        "SELECT p.last_known_name, s.races_played, s.races_won, s.rings_passed, s.boosts_used, "
                                + "s.resets, s.flight_millis FROM voyager.player_statistics s "
                                + "JOIN voyager.player p ON p.player_id = s.player_id ORDER BY p.last_known_name"))
                        .as("ranked statistics count every human racer's run")
                        .containsExactly(
                                List.of("Alex", 1L, 1L, 3L, 2L, 1L, 4_000L),
                                List.of("Bea", 1L, 0L, 2L, 0L, 0L, 0L));
            }
        }
    }

    @Test
    @DisplayName("a practice race writes its results but no personal best and no player statistics")
    void aPracticeRaceWritesItsResultsButNoPersonalBestAndNoPlayerStatistics() throws SQLException {
        String url = FreshDatabase.create(POSTGRES);
        RaceCompletion practice = race(MAP, GameMode.PRACTICE, 0,
                finisher(player(1), "Alex", 1, 61_200, MedalTier.GOLD, 0, 0, 0L, List.of(9_000, 18_000, 27_000)));
        try (StatsDatabase database = open(url)) {
            new PostgresCompletionStore(database.sessionFactory()).writeRace(practice);

            try (Connection connection = database.dataSource().getConnection()) {
                assertThat(SqlProbe.count(connection, "voyager.race_result"))
                        .as("the practice run is kept").isEqualTo(1);
                assertThat(SqlProbe.count(connection, "voyager.race_split"))
                        .as("its splits are kept").isEqualTo(3);
                assertThat(SqlProbe.count(connection, "voyager.map_personal_best"))
                        .as("no personal best is written for practice").isZero();
                assertThat(SqlProbe.count(connection, "voyager.player_statistics"))
                        .as("no statistics are written for practice").isZero();
            }
        }
    }

    @Test
    @DisplayName("a failure midway leaves no row of that race visible")
    void aFailureMidwayLeavesNoRowOfThatRaceVisible() throws SQLException {
        String url = FreshDatabase.create(POSTGRES);
        // The third racer's name is seventeen characters, one more than the player table allows: the write fails
        // after the session and the first two racers' rows are already inserted in the same transaction.
        RaceCompletion broken = race(MAP, GameMode.RACE, 0,
                finisher(player(1), "Alex", 1, 61_200, MedalTier.GOLD, 0, 0, 0L, List.of(9_000)),
                finisher(player(2), "Bea", 2, 62_000, MedalTier.SILVER, 0, 0, 0L, List.of(9_100)),
                finisher(player(3), "SeventeenLetters1", 3, 63_000, MedalTier.BRONZE, 0, 0, 0L, List.of(9_200)));
        try (StatsDatabase database = open(url)) {
            PostgresCompletionStore store = new PostgresCompletionStore(database.sessionFactory());

            assertThatThrownBy(() -> store.writeRace(broken))
                    .as("the write of the third racer fails")
                    .isInstanceOfSatisfying(StoreException.class,
                            failure -> assertThat(failure.sqlState())
                                    .as("a string that does not fit is a data error (class 22)")
                                    .isEqualTo("22001"));

            try (Connection connection = database.dataSource().getConnection()) {
                assertThat(SqlProbe.count(connection, "voyager.race_session"))
                        .as("no session of the failed race is visible").isZero();
                assertThat(SqlProbe.count(connection, "voyager.race_result"))
                        .as("no result of the failed race is visible").isZero();
                assertThat(SqlProbe.count(connection, "voyager.race_split"))
                        .as("no split of the failed race is visible").isZero();
                assertThat(SqlProbe.count(connection, "voyager.map_personal_best"))
                        .as("no personal best of the failed race is visible").isZero();
                assertThat(SqlProbe.count(connection, "voyager.player"))
                        .as("no player row of the failed race is visible").isZero();
            }

            store.writeRace(race(MAP, GameMode.RACE, 0,
                    finisher(player(1), "Alex", 1, 61_200, MedalTier.GOLD, 0, 0, 0L, List.of(9_000))));

            try (Connection connection = database.dataSource().getConnection()) {
                assertThat(SqlProbe.count(connection, "voyager.race_session"))
                        .as("the next good completion is written").isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("a computer racer is never stored")
    void aComputerRacerIsNeverStored() throws SQLException {
        String url = FreshDatabase.create(POSTGRES);
        RaceCompletion withBots = race(MAP, GameMode.RACE, 3,
                finisher(player(1), "Alex", 1, 61_200, MedalTier.GOLD, 0, 0, 0L, List.of(9_000)),
                finisher(player(2), "Bea", 2, 62_000, MedalTier.SILVER, 0, 0, 0L, List.of(9_100)));
        try (StatsDatabase database = open(url)) {
            new PostgresCompletionStore(database.sessionFactory()).writeRace(withBots);

            try (Connection connection = database.dataSource().getConnection()) {
                assertThat(SqlProbe.count(connection, "voyager.race_result"))
                        .as("only the two human racers have a result").isEqualTo(2);
                assertThat(SqlProbe.count(connection, "voyager.player"))
                        .as("only the two human racers have a player row").isEqualTo(2);
                assertThat(SqlProbe.one(connection, "SELECT bot_count FROM voyager.race_session"))
                        .as("the session keeps the number of computer racers as context only").isEqualTo(3);
            }
        }
    }

    @Test
    @DisplayName("one finished cup writes its session, its results and the ranked cup statistics")
    void oneFinishedCupWritesItsSessionResultsAndTheRankedCupStatistics() throws SQLException {
        String url = FreshDatabase.create(POSTGRES);
        CupCompletion completed = cup(CUP, GameMode.RACE,
                cupResult(player(1), "Alex", 1, 120, 2, 61_200, 1),
                cupResult(player(2), "Bea", 2, 60, 1, null, 0));
        try (StatsDatabase database = open(url)) {
            new PostgresCompletionStore(database.sessionFactory()).writeCup(completed);

            try (Connection connection = database.dataSource().getConnection()) {
                assertThat(SqlProbe.rows(connection,
                        "SELECT cup_key, game_mode, racer_count FROM voyager.cup_session"))
                        .containsExactly(List.of(CUP, "RACE", 2));
                assertThat(SqlProbe.rows(connection,
                        "SELECT p.last_known_name, cr.final_position::int, cr.total_points, cr.maps_finished, "
                                + "cr.best_time_millis, cr.medals_diamond FROM voyager.cup_result cr "
                                + "JOIN voyager.player p ON p.player_id = cr.player_id ORDER BY p.last_known_name"))
                        .containsExactly(
                                List.of("Alex", 1, 120, 2, 61_200, 1),
                                Arrays.asList("Bea", 2, 60, 1, null, 0));
                assertThat(SqlProbe.rows(connection,
                        "SELECT p.last_known_name, s.cups_played, s.cups_won FROM voyager.player_statistics s "
                                + "JOIN voyager.player p ON p.player_id = s.player_id ORDER BY p.last_known_name"))
                        .as("every human racer has one cup played, and only the winner has a cup won")
                        .containsExactly(List.of("Alex", 1L, 1L), List.of("Bea", 1L, 0L));
            }
        }
    }

    @Test
    @DisplayName("a practice cup writes its results and no cup statistics")
    void aPracticeCupWritesItsResultsAndNoCupStatistics() throws SQLException {
        String url = FreshDatabase.create(POSTGRES);
        CupCompletion practice = cup(CUP, GameMode.PRACTICE,
                cupResult(player(1), "Alex", 1, 120, 2, 61_200, 1));
        try (StatsDatabase database = open(url)) {
            new PostgresCompletionStore(database.sessionFactory()).writeCup(practice);

            try (Connection connection = database.dataSource().getConnection()) {
                assertThat(SqlProbe.count(connection, "voyager.cup_result"))
                        .as("the practice cup result is kept").isEqualTo(1);
                assertThat(SqlProbe.count(connection, "voyager.player_statistics"))
                        .as("no cup statistics are written for practice").isZero();
            }
        }
    }

    private static StatsDatabase open(String url) {
        return StatsDatabase.open(url, POSTGRES.getUsername(), POSTGRES.getPassword(), 2, false);
    }
}
