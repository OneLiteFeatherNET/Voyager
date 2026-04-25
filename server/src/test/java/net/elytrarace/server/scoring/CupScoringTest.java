package net.elytrarace.server.scoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Isolated tests for {@link CupScoring} logic.
 */
class CupScoringTest {

    private CupScoring cupScoring;

    @BeforeEach
    void setUp() {
        cupScoring = new CupScoring();
    }

    @Test
    void emptyScoring_hasZeroCompletedMaps() {
        assertThat(cupScoring.getCompletedMaps()).isZero();
    }

    @Test
    void emptyScoring_cupRankingIsEmpty() {
        assertThat(cupScoring.getCupRanking()).isEmpty();
    }

    @Test
    void addMapResult_increasesCompletedMaps() {
        UUID player = UUID.randomUUID();
        cupScoring.addMapResult(List.of(new PlayerScore(player, 10, 50, 60, PlayerScore.DNF_TIME, null)));

        assertThat(cupScoring.getCompletedMaps()).isEqualTo(1);
    }

    @Test
    void addMultipleMapResults_completedMapsMatchCount() {
        UUID player = UUID.randomUUID();
        cupScoring.addMapResult(List.of(new PlayerScore(player, 10, 50, 60, PlayerScore.DNF_TIME, null)));
        cupScoring.addMapResult(List.of(new PlayerScore(player, 20, 30, 50, PlayerScore.DNF_TIME, null)));
        cupScoring.addMapResult(List.of(new PlayerScore(player, 5, 20, 25, PlayerScore.DNF_TIME, null)));

        assertThat(cupScoring.getCompletedMaps()).isEqualTo(3);
    }

    @Test
    void getCupRanking_aggregatesTotalPointsAcrossMaps() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        // Map 1: player1=80, player2=50
        cupScoring.addMapResult(List.of(
                new PlayerScore(player1, 30, 50, 80, PlayerScore.DNF_TIME, null),
                new PlayerScore(player2, 20, 30, 50, PlayerScore.DNF_TIME, null)
        ));

        // Map 2: player1=40, player2=90
        cupScoring.addMapResult(List.of(
                new PlayerScore(player1, 10, 30, 40, PlayerScore.DNF_TIME, null),
                new PlayerScore(player2, 40, 50, 90, PlayerScore.DNF_TIME, null)
        ));

        List<PlayerScore> ranking = cupScoring.getCupRanking();

        assertThat(ranking).hasSize(2);
        // player2: 50 + 90 = 140
        assertThat(ranking.get(0).playerId()).isEqualTo(player2);
        assertThat(ranking.get(0).totalPoints()).isEqualTo(140);
        // player1: 80 + 40 = 120
        assertThat(ranking.get(1).playerId()).isEqualTo(player1);
        assertThat(ranking.get(1).totalPoints()).isEqualTo(120);
    }

    @Test
    void getCupRanking_sortedByTotalPointsDescending() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        UUID player3 = UUID.randomUUID();

        cupScoring.addMapResult(List.of(
                new PlayerScore(player1, 5, 10, 15, PlayerScore.DNF_TIME, null),
                new PlayerScore(player2, 20, 30, 50, PlayerScore.DNF_TIME, null),
                new PlayerScore(player3, 10, 20, 30, PlayerScore.DNF_TIME, null)
        ));

        List<PlayerScore> ranking = cupScoring.getCupRanking();

        assertThat(ranking.get(0).totalPoints()).isEqualTo(50);
        assertThat(ranking.get(1).totalPoints()).isEqualTo(30);
        assertThat(ranking.get(2).totalPoints()).isEqualTo(15);
    }

    @Test
    void getCupRanking_playerAppearingInOnlyOneMap() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        // Map 1: both players
        cupScoring.addMapResult(List.of(
                new PlayerScore(player1, 30, 50, 80, PlayerScore.DNF_TIME, null),
                new PlayerScore(player2, 20, 30, 50, PlayerScore.DNF_TIME, null)
        ));

        // Map 2: only player2
        cupScoring.addMapResult(List.of(
                new PlayerScore(player2, 40, 50, 90, PlayerScore.DNF_TIME, null)
        ));

        List<PlayerScore> ranking = cupScoring.getCupRanking();

        assertThat(ranking).hasSize(2);
        // player2: 50 + 90 = 140
        assertThat(ranking.get(0).playerId()).isEqualTo(player2);
        assertThat(ranking.get(0).totalPoints()).isEqualTo(140);
        // player1: 80 (only map 1)
        assertThat(ranking.get(1).playerId()).isEqualTo(player1);
        assertThat(ranking.get(1).totalPoints()).isEqualTo(80);
    }

    @Test
    void getCupRanking_singlePlayerSingleMap() {
        UUID player = UUID.randomUUID();

        cupScoring.addMapResult(List.of(new PlayerScore(player, 10, 50, 60, PlayerScore.DNF_TIME, null)));

        List<PlayerScore> ranking = cupScoring.getCupRanking();

        assertThat(ranking).hasSize(1);
        assertThat(ranking.get(0).playerId()).isEqualTo(player);
        assertThat(ranking.get(0).totalPoints()).isEqualTo(60);
    }

    @Test
    void getCupRanking_returnsUnmodifiableList() {
        UUID player = UUID.randomUUID();
        cupScoring.addMapResult(List.of(new PlayerScore(player, 10, 50, 60, PlayerScore.DNF_TIME, null)));

        List<PlayerScore> ranking = cupScoring.getCupRanking();

        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> ranking.add(new PlayerScore(UUID.randomUUID(), 0, 0, 0, PlayerScore.DNF_TIME, null))
        );
    }
}
