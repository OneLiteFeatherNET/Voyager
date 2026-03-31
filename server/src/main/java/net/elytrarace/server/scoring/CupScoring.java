package net.elytrarace.server.scoring;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregates player scores across multiple maps within a cup.
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
     * Returns the overall cup ranking by summing each player's totalPoints
     * across all completed maps, sorted in descending order.
     *
     * @return an unmodifiable list of aggregated player scores
     */
    public List<PlayerScore> getCupRanking() {
        Map<UUID, Integer> aggregated = new HashMap<>();
        for (List<PlayerScore> mapScores : mapResults) {
            for (PlayerScore score : mapScores) {
                aggregated.merge(score.playerId(), score.totalPoints(), Integer::sum);
            }
        }
        return aggregated.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                // Aggregated scores only carry totalPoints; per-map ringPoints and positionBonus
                // are not meaningful after summation across maps, so they are set to 0.
                .map(entry -> new PlayerScore(entry.getKey(), 0, 0, entry.getValue()))
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
}
