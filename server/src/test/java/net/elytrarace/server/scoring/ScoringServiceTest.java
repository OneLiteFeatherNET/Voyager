package net.elytrarace.server.scoring;

import net.elytrarace.server.physics.Ring;
import net.minestom.server.coordinate.Vec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringServiceTest {

    private static final Vec CENTER = new Vec(0, 10, 0);
    private static final Vec NORMAL = new Vec(0, 0, 1);
    private static final double RADIUS = 3.0;

    private ScoringServiceImpl scoringService;
    private CupScoring cupScoring;

    @BeforeEach
    void setUp() {
        scoringService = new ScoringServiceImpl();
        cupScoring = new CupScoring();
    }

    @Test
    void ringPassthroughIncreasesPoints() {
        UUID player = UUID.randomUUID();
        Ring ring = new Ring(CENTER, NORMAL, RADIUS, 10);

        scoringService.onRingPassed(player, ring);

        assertThat(scoringService.getScore(player).ringPoints()).isEqualTo(10);
        assertThat(scoringService.getScore(player).totalPoints()).isEqualTo(10);
    }

    @Test
    void multipleRingsAccumulatePoints() {
        UUID player = UUID.randomUUID();
        Ring ring5 = new Ring(CENTER, NORMAL, RADIUS, 5);
        Ring ring15 = new Ring(CENTER, NORMAL, RADIUS, 15);

        scoringService.onRingPassed(player, ring5);
        scoringService.onRingPassed(player, ring15);

        assertThat(scoringService.getScore(player).ringPoints()).isEqualTo(20);
        assertThat(scoringService.getScore(player).totalPoints()).isEqualTo(20);
    }

    @Test
    void rankingIsSortedByTotalPointsDescending() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        UUID player3 = UUID.randomUUID();
        Ring ring10 = new Ring(CENTER, NORMAL, RADIUS, 10);
        Ring ring20 = new Ring(CENTER, NORMAL, RADIUS, 20);
        Ring ring5 = new Ring(CENTER, NORMAL, RADIUS, 5);

        scoringService.onRingPassed(player1, ring10);
        scoringService.onRingPassed(player2, ring20);
        scoringService.onRingPassed(player3, ring5);

        List<PlayerScore> ranking = scoringService.getRanking();

        assertThat(ranking).hasSize(3);
        assertThat(ranking.get(0).playerId()).isEqualTo(player2);
        assertThat(ranking.get(1).playerId()).isEqualTo(player1);
        assertThat(ranking.get(2).playerId()).isEqualTo(player3);
    }

    @Test
    void positionBonusesAreCorrectlyApplied() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        UUID player3 = UUID.randomUUID();
        UUID player4 = UUID.randomUUID();

        scoringService.onRingPassed(player1, new Ring(CENTER, NORMAL, RADIUS, 40));
        scoringService.onRingPassed(player2, new Ring(CENTER, NORMAL, RADIUS, 30));
        scoringService.onRingPassed(player3, new Ring(CENTER, NORMAL, RADIUS, 20));
        scoringService.onRingPassed(player4, new Ring(CENTER, NORMAL, RADIUS, 10));

        scoringService.applyPositionBonuses();

        assertThat(scoringService.getScore(player1).positionBonus()).isEqualTo(50);
        assertThat(scoringService.getScore(player1).totalPoints()).isEqualTo(90);

        assertThat(scoringService.getScore(player2).positionBonus()).isEqualTo(30);
        assertThat(scoringService.getScore(player2).totalPoints()).isEqualTo(60);

        assertThat(scoringService.getScore(player3).positionBonus()).isEqualTo(20);
        assertThat(scoringService.getScore(player3).totalPoints()).isEqualTo(40);

        assertThat(scoringService.getScore(player4).positionBonus()).isEqualTo(10);
        assertThat(scoringService.getScore(player4).totalPoints()).isEqualTo(20);
    }

    @Test
    void resetClearsAllScores() {
        UUID player = UUID.randomUUID();
        scoringService.onRingPassed(player, new Ring(CENTER, NORMAL, RADIUS, 10));

        scoringService.reset();

        assertThat(scoringService.getRanking()).isEmpty();
        assertThat(scoringService.getScore(player).totalPoints()).isEqualTo(0);
    }

    @Test
    void unknownPlayerReturnsZeroScore() {
        UUID unknown = UUID.randomUUID();

        PlayerScore score = scoringService.getScore(unknown);

        assertThat(score.playerId()).isEqualTo(unknown);
        assertThat(score.ringPoints()).isEqualTo(0);
        assertThat(score.positionBonus()).isEqualTo(0);
        assertThat(score.totalPoints()).isEqualTo(0);
    }

    @Test
    void cupScoringAggregatesAcrossMaps() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        // Map 1: player1 wins
        scoringService.onRingPassed(player1, new Ring(CENTER, NORMAL, RADIUS, 30));
        scoringService.onRingPassed(player2, new Ring(CENTER, NORMAL, RADIUS, 20));
        scoringService.applyPositionBonuses();
        cupScoring.addMapResult(scoringService.getRanking());
        scoringService.reset();

        // Map 2: player2 wins
        scoringService.onRingPassed(player1, new Ring(CENTER, NORMAL, RADIUS, 10));
        scoringService.onRingPassed(player2, new Ring(CENTER, NORMAL, RADIUS, 40));
        scoringService.applyPositionBonuses();
        cupScoring.addMapResult(scoringService.getRanking());

        assertThat(cupScoring.getCompletedMaps()).isEqualTo(2);

        List<PlayerScore> cupRanking = cupScoring.getCupRanking();
        assertThat(cupRanking).hasSize(2);

        // player1: map1 = 30+50=80, map2 = 10+30=40 => 120
        // player2: map1 = 20+30=50, map2 = 40+50=90 => 140
        assertThat(cupRanking.get(0).playerId()).isEqualTo(player2);
        assertThat(cupRanking.get(0).totalPoints()).isEqualTo(140);
        assertThat(cupRanking.get(1).playerId()).isEqualTo(player1);
        assertThat(cupRanking.get(1).totalPoints()).isEqualTo(120);
    }
}
