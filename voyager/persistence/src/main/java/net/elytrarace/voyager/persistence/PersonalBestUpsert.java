package net.elytrarace.voyager.persistence;

import net.elytrarace.voyager.api.race.GameMode;
import org.hibernate.StatelessSession;

import java.time.Instant;
import java.util.UUID;

/**
 * Writes one finish into the personal bests of a map: one atomic statement, so two writers never lose the faster time
 * (design D4, Upserts).
 */
public final class PersonalBestUpsert {

    private static final String SQL = """
            INSERT INTO voyager.map_personal_best
                   (map_key, layout_version, game_mode, player_id, best_time_millis, race_result_id, achieved_at)
            VALUES (:mapKey, :layoutVersion, :gameMode, :playerId, :bestTimeMillis, :raceResultId, :achievedAt)
            ON CONFLICT (map_key, layout_version, game_mode, player_id) DO UPDATE SET
                best_time_millis = LEAST(voyager.map_personal_best.best_time_millis, EXCLUDED.best_time_millis),
                race_result_id   = CASE WHEN EXCLUDED.best_time_millis < voyager.map_personal_best.best_time_millis
                                          OR (EXCLUDED.best_time_millis = voyager.map_personal_best.best_time_millis
                                              AND EXCLUDED.achieved_at < voyager.map_personal_best.achieved_at)
                                        THEN EXCLUDED.race_result_id ELSE voyager.map_personal_best.race_result_id END,
                achieved_at      = CASE WHEN EXCLUDED.best_time_millis < voyager.map_personal_best.best_time_millis
                                          OR (EXCLUDED.best_time_millis = voyager.map_personal_best.best_time_millis
                                              AND EXCLUDED.achieved_at < voyager.map_personal_best.achieved_at)
                                        THEN EXCLUDED.achieved_at ELSE voyager.map_personal_best.achieved_at END
            """;

    private PersonalBestUpsert() {
    }

    /**
     * Records one finish. The caller opens the transaction.
     *
     * @param session   the stateless session whose transaction the write joins
     * @param candidate the finish to record
     */
    public static void upsert(StatelessSession session, Candidate candidate) {
        session.createNativeMutationQuery(SQL)
                .setParameter("mapKey", candidate.mapKey())
                .setParameter("layoutVersion", candidate.layoutVersion())
                .setParameter("gameMode", candidate.mode().name())
                .setParameter("playerId", candidate.playerId())
                .setParameter("bestTimeMillis", candidate.bestTimeMillis())
                .setParameter("raceResultId", candidate.raceResultId())
                .setParameter("achievedAt", candidate.achievedAt())
                .executeUpdate();
    }

    /**
     * One finish that may become the personal best of its player.
     *
     * @param mapKey        the map's name in its file
     * @param layoutVersion the layout identity of the map (design D7)
     * @param mode          the game mode of the run
     * @param playerId      the player's UUID
     * @param bestTimeMillis the finish time, never negative
     * @param raceResultId  the race result the finish belongs to
     * @param achievedAt    when the finish happened, from the injected clock of the race
     */
    public record Candidate(String mapKey, String layoutVersion, GameMode mode, UUID playerId, int bestTimeMillis,
            long raceResultId, Instant achievedAt) {

        public Candidate {
            if (mapKey == null || mapKey.isBlank()) {
                throw new IllegalArgumentException("map key must not be blank, was '%s'".formatted(mapKey));
            }
            if (bestTimeMillis < 0) {
                throw new IllegalArgumentException("best time must not be negative, was %s".formatted(bestTimeMillis));
            }
        }
    }
}
