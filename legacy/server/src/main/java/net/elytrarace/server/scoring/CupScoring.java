package net.elytrarace.server.scoring;

import net.elytrarace.common.game.scoring.MedalTier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregates player scores across multiple maps within a cup.
 *
 * <p>Each call to {@link #addMapResult(List)} stores an immutable snapshot of one
 * map's final ranking. {@link #getCupRanking()} sums {@code totalPoints} per
 * player across every recorded map and returns the players sorted by that sum.
 *
 * <p>For display purposes the aggregated entry also carries the player's best
 * (lowest) completion time across all completed maps. The medal tier on the
 * aggregated entry is intentionally {@code null} — medals only make sense per
 * map, not for a whole cup.
 */
public final class CupScoring {

    private final List<List<PlayerScore>> mapResults = new ArrayList<>();

    /**
     * Adds the final scores from a completed map.
     *
     * @param mapScores the ranked list of player scores for the completed map
     */
    public void addMapResult(List<PlayerScore> mapScores) {
        mapResults.add(List.copyOf(mapScores));
    }

    /**
     * Returns the overall cup ranking by summing each player's {@code totalPoints}
     * across all completed maps, sorted in descending order. The aggregated entry
     * also surfaces the player's best completion time across all maps for display.
     *
     * @return an unmodifiable list of aggregated player scores
     */
    public List<PlayerScore> getCupRanking() {
        Map<UUID, Aggregate> aggregated = new HashMap<>();
        for (List<PlayerScore> mapScores : mapResults) {
            for (PlayerScore score : mapScores) {
                aggregated.computeIfAbsent(score.playerId(), Aggregate::new).accept(score);
            }
        }
        return aggregated.values().stream()
                .sorted((a, b) -> Integer.compare(b.totalPoints, a.totalPoints))
                // Per-map ringPoints and positionBonus are not meaningful after summation
                // across maps, so they are set to 0. The best completion time is preserved
                // for display; medalTier stays null because medals are per-map only.
                .map(agg -> new PlayerScore(agg.playerId, 0, 0, agg.totalPoints, agg.bestTimeMs, null))
                .toList();
    }

    /**
     * Returns the number of maps that have been completed so far.
     *
     * @return the count of completed maps
     */
    public int getCompletedMaps() {
        return mapResults.size();
    }

    /**
     * Mutable accumulator used while folding scores into the cup ranking.
     * Tracks total points and the best (lowest, non-DNF) completion time.
     */
    private static final class Aggregate {

        private final UUID playerId;
        private int totalPoints;
        private long bestTimeMs = PlayerScore.DNF_TIME;

        private Aggregate(UUID playerId) {
            this.playerId = playerId;
        }

        private void accept(PlayerScore score) {
            totalPoints += score.totalPoints();
            long candidate = score.completionTimeMs();
            boolean candidateValid = candidate >= 0 && score.medalTier() != MedalTier.DNF;
            if (!candidateValid) {
                return;
            }
            if (bestTimeMs == PlayerScore.DNF_TIME || candidate < bestTimeMs) {
                bestTimeMs = candidate;
            }
        }
    }
}
