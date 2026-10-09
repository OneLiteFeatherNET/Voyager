package net.elytrarace.voyager.server.game;

import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.race.scoring.CupScore;
import net.elytrarace.voyager.race.scoring.CupScorer;
import net.elytrarace.voyager.race.scoring.MapScore;
import net.elytrarace.voyager.race.scoring.Placement;
import net.elytrarace.voyager.race.scoring.PlacementBonus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Every racer's score on every map of the cup so far, and the cup totals derived from it.
 *
 * <h2>Keyed by map index, not appended</h2>
 *
 * <p>A player's scores live in a map from map index to {@link MapScore} rather than in a list. The
 * difference shows the moment a cup is restarted while it is running — {@code /race start} does
 * exactly that — where an appending list would give the restarted map two rows and count it twice.
 * Recording by index makes a replay of map 2 replace map 2's score, which is what "restart" means.
 *
 * <h2>The placement bonus is applied here, once per map</h2>
 *
 * <p>{@code MapScorer} always produces a score with a zero placement bonus, because the bonus cannot
 * be known until every racer's run on that map is in. {@link #closeMap(int, GameMode)} is that
 * moment: it hands the whole field to {@code PlacementBonus.award} and writes the awarded scores
 * back, so a later read of a closed map sees the bonus and a re-read never awards it twice.
 *
 * <p>Not thread-safe: written from the tick thread and read from a command, which is the same thread
 * in Minestom.
 */
final class CupStandings {

    /**
     * The sort key standing in for "no completion time", so a racer who finished nothing lands
     * behind one who finished something on the same points. A day is longer than any race phase this
     * server will ever run and is never shown to anybody.
     */
    private static final java.time.Duration NO_TIME_SORTS_LAST = java.time.Duration.ofDays(1);

    /** Player -> map index -> that player's score on that map. Insertion order over players. */
    private final Map<UUID, Map<Integer, MapScore>> byPlayer = new LinkedHashMap<>();

    /** Records {@code score} as {@code playerId}'s result on map {@code mapIndex}, replacing any earlier one. */
    void record(UUID playerId, int mapIndex, MapScore score) {
        byPlayer.computeIfAbsent(playerId, ignored -> new TreeMap<>()).put(mapIndex, score);
    }

    /**
     * Awards the placement bonus for {@code mapIndex} across everyone who has a score on it, and
     * writes the awarded scores back.
     *
     * <p>Idempotent only in the sense that {@code PlacementBonus} adds to whatever bonus a score
     * already carries — so this must be called exactly once per map. It is, from
     * {@code CupSession.mapFinished}.
     */
    void closeMap(int mapIndex, GameMode mode) {
        List<Placement<UUID>> field = new ArrayList<>();
        byPlayer.forEach((playerId, scores) -> {
            MapScore score = scores.get(mapIndex);
            if (score != null) {
                field.add(new Placement<>(playerId, score));
            }
        });
        if (field.isEmpty()) {
            return;
        }
        for (Placement<UUID> awarded : PlacementBonus.award(field, mode)) {
            byPlayer.get(awarded.key()).put(mapIndex, awarded.score());
        }
    }

    /** {@code playerId}'s score on map {@code mapIndex}, or {@code null} if they have none. */
    @org.jetbrains.annotations.Nullable
    MapScore scoreOn(UUID playerId, int mapIndex) {
        Map<Integer, MapScore> scores = byPlayer.get(playerId);
        return scores == null ? null : scores.get(mapIndex);
    }

    /** {@code playerId}'s scores, one per map they have a result on, in map order. */
    List<MapScore> of(UUID playerId) {
        Map<Integer, MapScore> scores = byPlayer.get(playerId);
        return scores == null ? List.of() : List.copyOf(scores.values());
    }

    /** Everyone with at least one recorded map score. */
    Set<UUID> players() {
        return Set.copyOf(byPlayer.keySet());
    }

    /**
     * The cup standings, best first: every racer's {@link CupScorer} total, ordered by points
     * descending and then by best time ascending so a tie on points is broken by the faster racer
     * rather than by hash order. A racer with no completion time at all sorts behind every racer who
     * has one, on the same points.
     */
    List<CupStanding> cupOrder() {
        List<CupStanding> rows = new ArrayList<>();
        byPlayer.keySet().forEach(playerId -> rows.add(new CupStanding(playerId, totalOf(playerId))));
        rows.sort(Comparator
                .comparingInt((CupStanding row) -> row.score().totalPoints()).reversed()
                .thenComparing(row -> row.score().bestTime().orElse(NO_TIME_SORTS_LAST)));
        return List.copyOf(rows);
    }

    /** {@code playerId}'s cup total across every map they have a result on. */
    CupScore totalOf(UUID playerId) {
        return CupScorer.accumulate(of(playerId));
    }

    /** Drops everything — a fresh cup, not a continuation of the last one. */
    void clear() {
        byPlayer.clear();
    }
}
