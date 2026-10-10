package net.elytrarace.server.ecs.component;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreComponentTest {

    @Test
    void initialScoreIsZero() {
        var score = new ScoreComponent();

        assertThat(score.getRingPoints()).isZero();
        assertThat(score.getPositionBonus()).isZero();
        assertThat(score.getTotal()).isZero();
    }

    @Test
    void addRingPointsAccumulates() {
        var score = new ScoreComponent();

        score.addRingPoints(10);
        score.addRingPoints(5);

        assertThat(score.getRingPoints()).isEqualTo(15);
        assertThat(score.getTotal()).isEqualTo(15);
    }

    @Test
    void totalCombinesRingPointsAndPositionBonus() {
        var score = new ScoreComponent();

        score.addRingPoints(20);
        score.setPositionBonus(50);

        assertThat(score.getTotal()).isEqualTo(70);
    }

    @Test
    void resetClearsAllScores() {
        var score = new ScoreComponent();
        score.addRingPoints(30);
        score.setPositionBonus(10);

        score.reset();

        assertThat(score.getRingPoints()).isZero();
        assertThat(score.getPositionBonus()).isZero();
        assertThat(score.getTotal()).isZero();
    }
}
