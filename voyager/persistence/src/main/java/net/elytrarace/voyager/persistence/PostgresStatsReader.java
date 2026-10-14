package net.elytrarace.voyager.persistence;

import jakarta.persistence.Tuple;
import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.stats.LeaderboardEntry;
import net.elytrarace.voyager.api.stats.PlayerProfile;
import net.elytrarace.voyager.api.stats.StatsReader;
import net.elytrarace.voyager.api.stats.exception.StatsUnavailableException;
import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;
import org.hibernate.Transaction;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Reads statistics for {@code /stats} and {@code /leaderboard} from PostgreSQL (design D4). Every read runs in its own
 * read-only transaction on a stateless session of the shared pool; a failed read is reported as
 * {@link StatsUnavailableException}. Only ranked modes ({@link GameMode#ranked()}) are counted.
 */
public final class PostgresStatsReader implements StatsReader {

    /** Identity and running totals of one player; no row means the player is unknown. */
    static final String IDENTITY_SQL = """
            SELECT p.last_known_name,
                   COALESCE(s.cups_played, 0), COALESCE(s.cups_won, 0), COALESCE(s.flight_millis, 0)
            FROM voyager.player p
            LEFT JOIN voyager.player_statistics s ON s.player_id = p.player_id
            WHERE p.player_id = :playerId
            """;

    /** Medals of one player, counted from the ranked race results of design D4 (the statistics table has no medals). */
    static final String MEDALS_SQL = """
            SELECT rr.medal_tier, COUNT(*)
            FROM voyager.race_result rr
            JOIN voyager.race_session rs ON rs.race_session_id = rr.race_session_id
            WHERE rr.player_id = :playerId
              AND rs.game_mode IN (:rankedModes)
              AND rr.medal_tier IN ('DIAMOND', 'GOLD', 'SILVER', 'BRONZE')
            GROUP BY rr.medal_tier
            """;

    /** The personal bests of one player in ranked modes, every layout (the record names the layout of each). */
    static final String PERSONAL_BESTS_SQL = """
            SELECT pb.map_key, pb.layout_version, pb.game_mode, pb.best_time_millis, pb.achieved_at
            FROM voyager.map_personal_best pb
            WHERE pb.player_id = :playerId
              AND pb.game_mode IN (:rankedModes)
            ORDER BY pb.map_key, pb.layout_version, pb.game_mode
            """;

    /** The most recently seen player whose last known name matches, ignoring case. */
    static final String FIND_PLAYER_SQL = """
            SELECT p.player_id
            FROM voyager.player p
            WHERE lower(p.last_known_name) = lower(:name)
            ORDER BY p.last_seen DESC
            LIMIT 1
            """;

    /**
     * The leaderboard query of design D4: the top N personal bests of one map, one layout and one mode, fastest first.
     * The sort is served by {@code map_personal_best_leaderboard_idx}; the integration suite checks the plan.
     */
    static final String TOP_TIMES_SQL = """
            SELECT pb.player_id, p.last_known_name, pb.best_time_millis, pb.achieved_at
            FROM voyager.map_personal_best pb
            JOIN voyager.player p ON p.player_id = pb.player_id
            WHERE pb.map_key = :mapKey AND pb.layout_version = :layoutVersion AND pb.game_mode = :gameMode
            ORDER BY pb.best_time_millis ASC, pb.achieved_at ASC
            LIMIT :limit
            """;

    private static final List<String> RANKED_MODES = Arrays.stream(GameMode.values())
            .filter(GameMode::ranked)
            .map(Enum::name)
            .toList();

    private final SessionFactory sessionFactory;

    /**
     * @param sessionFactory the factory of the shared pool; the reader keeps no session of its own
     */
    public PostgresStatsReader(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public PlayerProfile profile(UUID playerId) {
        // The three queries always run, so an unknown player gets the same statement path as a known one.
        return read(session -> {
            List<Tuple> identity = session.createNativeQuery(IDENTITY_SQL, Tuple.class)
                    .setParameter("playerId", playerId)
                    .getResultList();
            Map<String, Long> medals = medalCounts(session, playerId);
            List<PlayerProfile.PersonalBest> bests = personalBests(session, playerId);
            if (identity.isEmpty()) {
                return new PlayerProfile(playerId, null, 0, 0, 0, 0, 0, 0, 0L, bests);
            }
            Tuple totals = identity.getFirst();
            return new PlayerProfile(
                    playerId,
                    (String) totals.get(0),
                    Math.toIntExact(number(totals, 1)),
                    Math.toIntExact(number(totals, 2)),
                    Math.toIntExact(medals.getOrDefault("DIAMOND", 0L)),
                    Math.toIntExact(medals.getOrDefault("GOLD", 0L)),
                    Math.toIntExact(medals.getOrDefault("SILVER", 0L)),
                    Math.toIntExact(medals.getOrDefault("BRONZE", 0L)),
                    number(totals, 3),
                    bests);
        });
    }

    @Override
    public Optional<UUID> findPlayer(String name) {
        return read(session -> session.createNativeQuery(FIND_PLAYER_SQL, UUID.class)
                .setParameter("name", name)
                .getResultList()
                .stream()
                .findFirst());
    }

    @Override
    public List<LeaderboardEntry> topTimes(String mapKey, String layoutVersion, GameMode mode, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException(
                    "a leaderboard lists at least one entry, was asked for %s".formatted(limit));
        }
        if (!mode.ranked()) {
            return List.of();
        }
        return read(session -> {
            List<Tuple> rows = session.createNativeQuery(TOP_TIMES_SQL, Tuple.class)
                    .setParameter("mapKey", mapKey)
                    .setParameter("layoutVersion", layoutVersion)
                    .setParameter("gameMode", mode.name())
                    .setParameter("limit", limit)
                    .getResultList();
            List<LeaderboardEntry> entries = new ArrayList<>(rows.size());
            for (int index = 0; index < rows.size(); index++) {
                Tuple row = rows.get(index);
                entries.add(new LeaderboardEntry(
                        index + 1,
                        (UUID) row.get(0),
                        (String) row.get(1),
                        Math.toIntExact(number(row, 2)),
                        instant(row.get(3))));
            }
            return List.copyOf(entries);
        });
    }

    private static Map<String, Long> medalCounts(StatelessSession session, UUID playerId) {
        List<Tuple> rows = session.createNativeQuery(MEDALS_SQL, Tuple.class)
                .setParameter("playerId", playerId)
                .setParameterList("rankedModes", RANKED_MODES)
                .getResultList();
        Map<String, Long> counts = new HashMap<>();
        for (Tuple row : rows) {
            counts.put((String) row.get(0), number(row, 1));
        }
        return counts;
    }

    private static List<PlayerProfile.PersonalBest> personalBests(StatelessSession session, UUID playerId) {
        List<Tuple> rows = session.createNativeQuery(PERSONAL_BESTS_SQL, Tuple.class)
                .setParameter("playerId", playerId)
                .setParameterList("rankedModes", RANKED_MODES)
                .getResultList();
        List<PlayerProfile.PersonalBest> bests = new ArrayList<>(rows.size());
        for (Tuple row : rows) {
            bests.add(new PlayerProfile.PersonalBest(
                    (String) row.get(0),
                    (String) row.get(1),
                    GameMode.valueOf((String) row.get(2)),
                    Math.toIntExact(number(row, 3)),
                    instant(row.get(4))));
        }
        return List.copyOf(bests);
    }

    /**
     * Runs one query function in a read-only transaction on a new stateless session and returns its result. Any
     * failure of the database surfaces as {@link StatsUnavailableException}; the transaction is rolled back first.
     */
    private <T> T read(Function<StatelessSession, T> query) {
        try (StatelessSession session = sessionFactory.openStatelessSession()) {
            Transaction transaction = session.beginTransaction();
            try {
                session.createNativeMutationQuery("SET TRANSACTION READ ONLY").executeUpdate();
                T result = query.apply(session);
                transaction.commit();
                return result;
            } finally {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
            }
        } catch (RuntimeException failure) {
            throw new StatsUnavailableException("the statistics query failed: %s"
                    .formatted(failure.getClass().getSimpleName()), failure);
        }
    }

    private static long number(Tuple row, int index) {
        return ((Number) row.get(index)).longValue();
    }

    private static Instant instant(Object value) {
        if (value instanceof OffsetDateTime offset) {
            return offset.toInstant();
        }
        return (Instant) value;
    }
}
