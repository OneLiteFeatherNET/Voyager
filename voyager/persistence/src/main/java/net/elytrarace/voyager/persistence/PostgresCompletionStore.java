package net.elytrarace.voyager.persistence;

import jakarta.persistence.PersistenceException;
import net.elytrarace.voyager.api.stats.CupCompletion;
import net.elytrarace.voyager.api.stats.CupRacerResult;
import net.elytrarace.voyager.api.stats.RaceCompletion;
import net.elytrarace.voyager.api.stats.RacerRun;
import net.elytrarace.voyager.persistence.exception.StoreException;
import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;
import org.hibernate.Transaction;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Writes finished maps and cups to PostgreSQL (design D4, D5). Each completion is one transaction on a stateless
 * session of the shared pool: when any statement fails, the transaction is rolled back, so no part of the completion
 * is ever visible.
 *
 * <p>The statements are native SQL, as {@link PersonalBestUpsert} is: the entities are mappings for validation and
 * reads, and carry no constructors that a writer could use.
 *
 * <p>Achieved-at of a personal best is the map's start plus the racer's finish time: a run's finish time is measured
 * from the start of its map, so that instant is the finish. Practice runs are stored per run and touch neither the
 * personal bests nor the player statistics (design D7).
 */
public final class PostgresCompletionStore implements CompletionStore {

    private static final String UPSERT_PLAYER_SQL = """
            INSERT INTO voyager.player (player_id, last_known_name, first_seen, last_seen)
            VALUES (:playerId, :name, :seenAt, :seenAt)
            ON CONFLICT (player_id) DO UPDATE SET
                last_known_name = CASE WHEN EXCLUDED.last_seen >= voyager.player.last_seen
                                       THEN EXCLUDED.last_known_name ELSE voyager.player.last_known_name END,
                last_seen       = GREATEST(voyager.player.last_seen, EXCLUDED.last_seen)
            """;

    private static final String INSERT_RACE_SESSION_SQL = """
            INSERT INTO voyager.race_session
                   (map_key, layout_version, game_mode, started_at, finished_at, human_count, bot_count)
            VALUES (:mapKey, :layoutVersion, :gameMode, :startedAt, :finishedAt, :humanCount, :botCount)
            RETURNING race_session_id
            """;

    private static final String INSERT_RACE_RESULT_SQL = """
            INSERT INTO voyager.race_result
                   (race_session_id, player_id, finish_position, finish_time_millis, ring_points, placement_bonus,
                    medal_points, total_points, rings_passed, boosts_used, resets, flight_millis, medal_tier,
                    validation_state)
            VALUES (:raceSessionId, :playerId, CAST(:finishPosition AS smallint), CAST(:finishTimeMillis AS integer),
                    :ringPoints, :placementBonus, :medalPoints, :totalPoints, :ringsPassed, :boosts, :resets,
                    :flightMillis, :medalTier, 'VALID')
            RETURNING race_result_id
            """;

    private static final String INSERT_SPLIT_SQL = """
            INSERT INTO voyager.race_split (race_result_id, ring_index, game_tick)
            VALUES (:raceResultId, :ringIndex, :gameTick)
            """;

    private static final String UPSERT_RACE_STATISTICS_SQL = """
            INSERT INTO voyager.player_statistics
                   (player_id, races_played, races_won, rings_passed, boosts_used, resets, flight_millis)
            VALUES (:playerId, 1, :won, :ringsPassed, :boosts, :resets, :flightMillis)
            ON CONFLICT (player_id) DO UPDATE SET
                races_played  = voyager.player_statistics.races_played + 1,
                races_won     = voyager.player_statistics.races_won + EXCLUDED.races_won,
                rings_passed  = voyager.player_statistics.rings_passed + EXCLUDED.rings_passed,
                boosts_used   = voyager.player_statistics.boosts_used + EXCLUDED.boosts_used,
                resets        = voyager.player_statistics.resets + EXCLUDED.resets,
                flight_millis = voyager.player_statistics.flight_millis + EXCLUDED.flight_millis
            """;

    private static final String INSERT_CUP_SESSION_SQL = """
            INSERT INTO voyager.cup_session (cup_key, game_mode, started_at, finished_at, racer_count)
            VALUES (:cupKey, :gameMode, :startedAt, :finishedAt, :racerCount)
            RETURNING cup_session_id
            """;

    private static final String INSERT_CUP_RESULT_SQL = """
            INSERT INTO voyager.cup_result
                   (cup_session_id, player_id, final_position, total_points, maps_finished, best_time_millis,
                    medals_diamond, medals_gold, medals_silver, medals_bronze)
            VALUES (:cupSessionId, :playerId, :finalPosition, :totalPoints, :mapsFinished,
                    CAST(:bestTimeMillis AS integer), :diamonds, :golds, :silvers, :bronzes)
            """;

    private static final String UPSERT_CUP_STATISTICS_SQL = """
            INSERT INTO voyager.player_statistics (player_id, cups_played, cups_won)
            VALUES (:playerId, 1, :won)
            ON CONFLICT (player_id) DO UPDATE SET
                cups_played = voyager.player_statistics.cups_played + 1,
                cups_won    = voyager.player_statistics.cups_won + EXCLUDED.cups_won
            """;

    private final SessionFactory sessionFactory;

    /**
     * @param sessionFactory the factory of the shared pool; every write opens its own stateless session
     */
    public PostgresCompletionStore(SessionFactory sessionFactory) {
        this.sessionFactory = Objects.requireNonNull(sessionFactory, "session factory must not be null");
    }

    @Override
    public void writeRace(RaceCompletion completion) {
        String what = completion.cupKey() == null
                ? "map %s".formatted(completion.mapKey())
                : "map %s of cup %s".formatted(completion.mapKey(), completion.cupKey());
        inTransaction(what, session -> writeRace(session, completion));
    }

    @Override
    public void writeCup(CupCompletion completion) {
        inTransaction("cup %s".formatted(completion.cupKey()), session -> writeCup(session, completion));
    }

    private static void writeRace(StatelessSession session, RaceCompletion completion) {
        long raceSessionId = session.createNativeQuery(INSERT_RACE_SESSION_SQL, Long.class)
                .setParameter("mapKey", completion.mapKey())
                .setParameter("layoutVersion", completion.layoutVersion())
                .setParameter("gameMode", completion.mode().name())
                .setParameter("startedAt", completion.startedAt())
                .setParameter("finishedAt", completion.finishedAt())
                .setParameter("humanCount", completion.humanCount())
                .setParameter("botCount", completion.botCount())
                .getSingleResult();
        boolean ranked = completion.mode().ranked();
        for (RacerRun run : completion.racers()) {
            upsertPlayer(session, run.playerId(), run.name(), completion.finishedAt());
            long raceResultId = insertRaceResult(session, raceSessionId, run);
            insertSplits(session, raceResultId, run.splitTicks());
            if (!ranked) {
                continue;
            }
            if (run.finishTimeMillis() != null) {
                PersonalBestUpsert.upsert(session, new PersonalBestUpsert.Candidate(
                        completion.mapKey(), completion.layoutVersion(), completion.mode(), run.playerId(),
                        run.finishTimeMillis(), raceResultId,
                        completion.startedAt().plusMillis(run.finishTimeMillis())));
            }
            upsertRaceStatistics(session, run);
        }
    }

    private static void writeCup(StatelessSession session, CupCompletion completion) {
        long cupSessionId = session.createNativeQuery(INSERT_CUP_SESSION_SQL, Long.class)
                .setParameter("cupKey", completion.cupKey())
                .setParameter("gameMode", completion.mode().name())
                .setParameter("startedAt", completion.startedAt())
                .setParameter("finishedAt", completion.finishedAt())
                .setParameter("racerCount", completion.racers().size())
                .getSingleResult();
        for (CupRacerResult racer : completion.racers()) {
            upsertPlayer(session, racer.playerId(), racer.name(), completion.finishedAt());
            insertCupResult(session, cupSessionId, racer);
            if (completion.mode().ranked()) {
                upsertCupStatistics(session, racer);
            }
        }
    }

    private static void upsertPlayer(StatelessSession session, UUID playerId, String name, Instant seenAt) {
        session.createNativeMutationQuery(UPSERT_PLAYER_SQL)
                .setParameter("playerId", playerId)
                .setParameter("name", name)
                .setParameter("seenAt", seenAt)
                .executeUpdate();
    }

    private static long insertRaceResult(StatelessSession session, long raceSessionId, RacerRun run) {
        Integer finishPosition = run.finishPosition();
        Integer finishTimeMillis = run.finishTimeMillis();
        return session.createNativeQuery(INSERT_RACE_RESULT_SQL, Long.class)
                .setParameter("raceSessionId", raceSessionId)
                .setParameter("playerId", run.playerId())
                .setParameter("finishPosition", finishPosition)
                .setParameter("finishTimeMillis", finishTimeMillis)
                .setParameter("ringPoints", run.ringPoints())
                .setParameter("placementBonus", run.placementBonus())
                .setParameter("medalPoints", run.medalPoints())
                .setParameter("totalPoints", run.ringPoints() + run.placementBonus() + run.medalPoints())
                .setParameter("ringsPassed", run.ringsPassed())
                .setParameter("boosts", run.boosts())
                .setParameter("resets", run.resets())
                .setParameter("flightMillis", Math.toIntExact(run.flightMillis()))
                .setParameter("medalTier", run.medalTier().name())
                .getSingleResult();
    }

    private static void insertSplits(StatelessSession session, long raceResultId, List<Integer> splitTicks) {
        for (int ringIndex = 0; ringIndex < splitTicks.size(); ringIndex++) {
            session.createNativeMutationQuery(INSERT_SPLIT_SQL)
                    .setParameter("raceResultId", raceResultId)
                    .setParameter("ringIndex", ringIndex)
                    .setParameter("gameTick", splitTicks.get(ringIndex))
                    .executeUpdate();
        }
    }

    private static void upsertRaceStatistics(StatelessSession session, RacerRun run) {
        session.createNativeMutationQuery(UPSERT_RACE_STATISTICS_SQL)
                .setParameter("playerId", run.playerId())
                .setParameter("won", run.finishPosition() != null && run.finishPosition() == 1 ? 1 : 0)
                .setParameter("ringsPassed", run.ringsPassed())
                .setParameter("boosts", run.boosts())
                .setParameter("resets", run.resets())
                .setParameter("flightMillis", run.flightMillis())
                .executeUpdate();
    }

    private static void insertCupResult(StatelessSession session, long cupSessionId, CupRacerResult racer) {
        Integer bestTimeMillis = racer.bestTimeMillis();
        session.createNativeMutationQuery(INSERT_CUP_RESULT_SQL)
                .setParameter("cupSessionId", cupSessionId)
                .setParameter("playerId", racer.playerId())
                .setParameter("finalPosition", racer.finalPosition())
                .setParameter("totalPoints", racer.totalPoints())
                .setParameter("mapsFinished", racer.mapsFinished())
                .setParameter("bestTimeMillis", bestTimeMillis)
                .setParameter("diamonds", racer.diamonds())
                .setParameter("golds", racer.golds())
                .setParameter("silvers", racer.silvers())
                .setParameter("bronzes", racer.bronzes())
                .executeUpdate();
    }

    private static void upsertCupStatistics(StatelessSession session, CupRacerResult racer) {
        session.createNativeMutationQuery(UPSERT_CUP_STATISTICS_SQL)
                .setParameter("playerId", racer.playerId())
                .setParameter("won", racer.finalPosition() == 1 ? 1 : 0)
                .executeUpdate();
    }

    /**
     * Runs one write in one transaction. A Hibernate or JDBC failure leaves as {@link StoreException} with the SQLSTATE
     * of the driver; the transaction is rolled back before it leaves. Other runtime failures are not wrapped.
     */
    private void inTransaction(String what, Consumer<StatelessSession> write) {
        try (StatelessSession session = sessionFactory.openStatelessSession()) {
            Transaction transaction = session.beginTransaction();
            try {
                write.accept(session);
                transaction.commit();
            } catch (RuntimeException failure) {
                rollback(transaction, failure);
                throw failure;
            }
        } catch (PersistenceException failure) {
            throw new StoreException("could not write %s: %s".formatted(what, failure.getClass().getSimpleName()),
                    sqlStateOf(failure), failure);
        }
    }

    private static void rollback(Transaction transaction, RuntimeException failure) {
        if (!transaction.isActive()) {
            return;
        }
        try {
            transaction.rollback();
        } catch (RuntimeException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    /** The SQLSTATE of the first driver exception in the cause chain, or {@code null} when there is none. */
    @Nullable
    private static String sqlStateOf(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException && sqlException.getSQLState() != null) {
                return sqlException.getSQLState();
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return null;
    }
}
