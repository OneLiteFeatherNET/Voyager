package net.elytrarace.voyager.race.scoring;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.MedalTier;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.race.progress.RingProgress;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MapScorerTest {

    // Ring points are 999, deliberately different from MapScorer's flat 10-per-ring: if scoring ever
    // summed a ring's own points() instead of using its own constant, these fixtures would catch it.
    private static Ring ring(int index) {
        return new Ring(index, new Vec3(0.0, 64.0, index * 10.0), new Vec3(0.0, 0.0, 1.0), 5.0, 999,
                RingType.STANDARD);
    }

    // Two rings, a sixty-second reference time.
    private static final MapDefinition TWO_RING_MAP = new MapDefinition("two-ring", "world_a", Vec3.ZERO,
            List.of(ring(0), ring(1)), Duration.ofSeconds(60));

    // Three rings, a thirty-second reference time — deliberately different from TWO_RING_MAP on both
    // the ring count and the reference time, so a scorer hardcoding either cannot pass every test.
    private static final MapDefinition THREE_RING_MAP = new MapDefinition("three-ring", "world_b", Vec3.ZERO,
            List.of(ring(0), ring(1), ring(2)), Duration.ofSeconds(30));

    @Test
    void scoresAFinishedRunAsRingPointsPlusMedalPoints() {
        RingProgress finished = new RingProgress(2, -1);
        Duration completionTime = Duration.ofSeconds(50);

        MapScore score = MapScorer.score(finished, TWO_RING_MAP, completionTime);

        assertThat(score.ringPoints()).isEqualTo(20);
        assertThat(score.medal()).isEqualTo(MedalTier.DIAMOND);
        assertThat(score.medalPoints()).isEqualTo(60);
        assertThat(score.placementBonus()).isZero();
        assertThat(score.completionTime()).isEqualTo(completionTime);
        assertThat(score.total()).isEqualTo(80);
    }

    @Test
    void classifiesTheMedalAgainstTheMapsOwnReferenceTimeRatherThanAFixedOne() {
        // 33s against THREE_RING_MAP's 30s reference is exactly the gold boundary (30s x 1.10) — a
        // scorer that hardcoded TWO_RING_MAP's 60s here would land on a different tier entirely.
        RingProgress finished = new RingProgress(3, -1);
        Duration completionTime = Duration.ofSeconds(33);

        MapScore score = MapScorer.score(finished, THREE_RING_MAP, completionTime);

        assertThat(score.ringPoints()).isEqualTo(30);
        assertThat(score.medal()).isEqualTo(MedalTier.GOLD);
        assertThat(score.medalPoints()).isEqualTo(45);
        assertThat(score.completionTime()).isEqualTo(completionTime);
        assertThat(score.total()).isEqualTo(75);
    }

    @Test
    void scoresAnUnfinishedRunAsRingPointsOnlyWithDidNotFinish() {
        // One of three rings passed: ringPoints must come from passedCount (10), not rings.size() * 10
        // (30).
        RingProgress unfinished = new RingProgress(1, -1);
        Duration completionTime = Duration.ofSeconds(20);

        MapScore score = MapScorer.score(unfinished, THREE_RING_MAP, completionTime);

        assertThat(score.ringPoints()).isEqualTo(10);
        assertThat(score.medal()).isEqualTo(MedalTier.DNF);
        assertThat(score.medalPoints()).isZero();
        assertThat(score.placementBonus()).isZero();
        assertThat(score.completionTime()).isEqualTo(completionTime);
        assertThat(score.total()).isEqualTo(10);
    }

    @Test
    void carriesTheCompletionTimeThroughUnchangedWhetherFinishedOrNot() {
        Duration oddDuration = Duration.ofSeconds(12).plusNanos(345);

        MapScore finishedScore = MapScorer.score(new RingProgress(2, -1), TWO_RING_MAP, oddDuration);
        MapScore unfinishedScore = MapScorer.score(new RingProgress(0, -1), TWO_RING_MAP, oddDuration);

        assertThat(finishedScore.completionTime()).isEqualTo(oddDuration);
        assertThat(unfinishedScore.completionTime()).isEqualTo(oddDuration);
    }
}
