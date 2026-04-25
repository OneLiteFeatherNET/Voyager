package net.elytrarace.server.scoring;

import net.elytrarace.common.game.scoring.MedalBrackets;
import net.elytrarace.common.game.scoring.MedalTier;
import net.elytrarace.server.physics.Ring;
import net.minestom.server.coordinate.Vec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RaceScoringStrategy} — the concrete scoring implementation
 * used in Race Mode.
 */
class ScoringServiceTest {

    private static final Vec CENTER = new Vec(0, 10, 0);
    private static final Vec NORMAL = new Vec(0, 0, 1);
    private static final double RADIUS = 3.0;
    private static final Duration REFERENCE = Duration.ofMinutes(3);

    private RaceScoringStrategy strategy;
    private CupScoring cupScoring;

    @BeforeEach
    void setUp() {
        strategy = new RaceScoringStrategy();
        cupScoring = new CupScoring();
    }

    @Test
    void ringPassthroughIncreasesPoints() {
        UUID player = UUID.randomUUID();
        Ring ring = new Ring(CENTER, NORMAL, RADIUS, 10);

        strategy.onRingPassed(player, ring);

        assertThat(strategy.getScore(player).ringPoints()).isEqualTo(10);
        assertThat(strategy.getScore(player).totalPoints()).isEqualTo(10);
    }

    @Test
    void multipleRingsAccumulatePoints() {
        UUID player = UUID.randomUUID();
        Ring ring5 = new Ring(CENTER, NORMAL, RADIUS, 5);
        Ring ring15 = new Ring(CENTER, NORMAL, RADIUS, 15);

        strategy.onRingPassed(player, ring5);
        strategy.onRingPassed(player, ring15);

        assertThat(strategy.getScore(player).ringPoints()).isEqualTo(20);
        assertThat(strategy.getScore(player).totalPoints()).isEqualTo(20);
    }

    @Test
    void rankingIsSortedByTotalPointsDescending() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        UUID player3 = UUID.randomUUID();

        strategy.onRingPassed(player1, new Ring(CENTER, NORMAL, RADIUS, 10));
        strategy.onRingPassed(player2, new Ring(CENTER, NORMAL, RADIUS, 20));
        strategy.onRingPassed(player3, new Ring(CENTER, NORMAL, RADIUS, 5));

        List<PlayerScore> ranking = strategy.getRanking();

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

        strategy.onRingPassed(player1, new Ring(CENTER, NORMAL, RADIUS, 40));
        strategy.onRingPassed(player2, new Ring(CENTER, NORMAL, RADIUS, 30));
        strategy.onRingPassed(player3, new Ring(CENTER, NORMAL, RADIUS, 20));
        strategy.onRingPassed(player4, new Ring(CENTER, NORMAL, RADIUS, 10));

        strategy.applyMapResults();

        // RaceScoringStrategy: 1st=10, 2nd=6, 3rd=3, 4th+=1
        assertThat(strategy.getScore(player1).positionBonus()).isEqualTo(10);
        assertThat(strategy.getScore(player1).totalPoints()).isEqualTo(50);

        assertThat(strategy.getScore(player2).positionBonus()).isEqualTo(6);
        assertThat(strategy.getScore(player2).totalPoints()).isEqualTo(36);

        assertThat(strategy.getScore(player3).positionBonus()).isEqualTo(3);
        assertThat(strategy.getScore(player3).totalPoints()).isEqualTo(23);

        assertThat(strategy.getScore(player4).positionBonus()).isEqualTo(1);
        assertThat(strategy.getScore(player4).totalPoints()).isEqualTo(11);
    }

    @Test
    void resetClearsAllScores() {
        UUID player = UUID.randomUUID();
        strategy.onRingPassed(player, new Ring(CENTER, NORMAL, RADIUS, 10));

        strategy.reset();

        assertThat(strategy.getRanking()).isEmpty();
        assertThat(strategy.getScore(player).totalPoints()).isEqualTo(0);
    }

    @Test
    void unknownPlayerReturnsZeroScore() {
        UUID unknown = UUID.randomUUID();

        PlayerScore score = strategy.getScore(unknown);

        assertThat(score.playerId()).isEqualTo(unknown);
        assertThat(score.ringPoints()).isEqualTo(0);
        assertThat(score.positionBonus()).isEqualTo(0);
        assertThat(score.totalPoints()).isEqualTo(0);
    }

    @Test
    void onMapCompleted_diamondBracket_awards60BracketPoints() {
        UUID player = UUID.randomUUID();
        long elapsedMs = REFERENCE.toMillis(); // exactly at reference = DIAMOND

        strategy.onMapCompleted(player, elapsedMs, MedalBrackets.DEFAULT, REFERENCE);

        assertThat(strategy.getScore(player).medalTier()).isEqualTo(MedalTier.DIAMOND);
        assertThat(strategy.getScore(player).positionBonus()).isEqualTo(60);
    }

    @Test
    void onMapCompleted_goldBracket_awards45BracketPoints() {
        UUID player = UUID.randomUUID();
        // 105% of reference → GOLD bracket
        long elapsedMs = (long) (REFERENCE.toMillis() * 1.05);

        strategy.onMapCompleted(player, elapsedMs, MedalBrackets.DEFAULT, REFERENCE);

        assertThat(strategy.getScore(player).medalTier()).isEqualTo(MedalTier.GOLD);
        assertThat(strategy.getScore(player).positionBonus()).isEqualTo(45);
    }

    @Test
    void onMapCompleted_dnfElapsedTime_awardsDnfTierAndZeroPoints() {
        UUID player = UUID.randomUUID();

        strategy.onMapCompleted(player, PlayerScore.DNF_TIME, MedalBrackets.DEFAULT, REFERENCE);

        assertThat(strategy.getScore(player).medalTier()).isEqualTo(MedalTier.DNF);
        assertThat(strategy.getScore(player).positionBonus()).isEqualTo(0);
    }

    @Test
    void cupScoringAggregatesAcrossMaps() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        // Map 1: player1 gets more ring points
        strategy.onRingPassed(player1, new Ring(CENTER, NORMAL, RADIUS, 30));
        strategy.onRingPassed(player2, new Ring(CENTER, NORMAL, RADIUS, 20));
        strategy.applyMapResults();
        cupScoring.addMapResult(strategy.getRanking());
        strategy.reset();

        // Map 2: player2 gets more ring points
        strategy.onRingPassed(player1, new Ring(CENTER, NORMAL, RADIUS, 10));
        strategy.onRingPassed(player2, new Ring(CENTER, NORMAL, RADIUS, 40));
        strategy.applyMapResults();
        cupScoring.addMapResult(strategy.getRanking());

        assertThat(cupScoring.getCompletedMaps()).isEqualTo(2);

        List<PlayerScore> cupRanking = cupScoring.getCupRanking();
        assertThat(cupRanking).hasSize(2);
        // player2 accumulates more total across both maps
        assertThat(cupRanking.get(0).playerId()).isEqualTo(player2);
        assertThat(cupRanking.get(1).playerId()).isEqualTo(player1);
    }
}
