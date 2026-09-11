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

    // Every ring carries a different point value and none of them is 10, the flat rate this scorer
    // used to pay regardless. No partial sum of a leading run of rings equals ten times its length
    // either — 9, 34, 4, 21, 52 are all distinct from 10, 20, 30 — so a flat rate cannot pass any
    // assertion here by coincidence.
    private static Ring ring(int index, int points) {
        return new Ring(index, new Vec3(0.0, 64.0, index * 10.0), new Vec3(0.0, 0.0, 1.0), 5.0, points,
                RingType.STANDARD);
    }

    // Two rings worth 9 and 25 — 34 in total — and a sixty-second reference time.
    private static final MapDefinition TWO_RING_MAP = new MapDefinition("two-ring", "world_a", Vec3.ZERO,
            List.of(ring(0, 9), ring(1, 25)), Duration.ofSeconds(60));

    // Three rings worth 4, 17 and 31 — 52 in total — and a thirty-second reference time, deliberately
    // different from TWO_RING_MAP on the ring count, the point values and the reference time, so a
    // scorer hardcoding any of them cannot pass every test. The values also rise across the course,
    // so summing the LAST n rings instead of the first n (48 rather than 21 for two of three) is
    // distinguishable.
    private static final MapDefinition THREE_RING_MAP = new MapDefinition("three-ring", "world_b", Vec3.ZERO,
            List.of(ring(0, 4), ring(1, 17), ring(2, 31)), Duration.ofSeconds(30));

    @Test
    void scoresAFinishedRunAsRingPointsPlusMedalPoints() {
        RingProgress finished = new RingProgress(2, -1);
        Duration completionTime = Duration.ofSeconds(50);

        MapScore score = MapScorer.score(finished, TWO_RING_MAP, completionTime);

        assertThat(score.ringPoints()).as("9 + 25, the map's own two ring values").isEqualTo(34);
        assertThat(score.medal()).isEqualTo(MedalTier.DIAMOND);
        assertThat(score.medalPoints()).isEqualTo(60);
        assertThat(score.placementBonus()).isZero();
        assertThat(score.completionTime()).contains(completionTime);
        assertThat(score.total()).isEqualTo(94);
    }

    @Test
    void classifiesTheMedalAgainstTheMapsOwnReferenceTimeRatherThanAFixedOne() {
        // 33s against THREE_RING_MAP's 30s reference is exactly the gold boundary (30s x 1.10) — a
        // scorer that hardcoded TWO_RING_MAP's 60s here would land on a different tier entirely.
        RingProgress finished = new RingProgress(3, -1);
        Duration completionTime = Duration.ofSeconds(33);

        MapScore score = MapScorer.score(finished, THREE_RING_MAP, completionTime);

        assertThat(score.ringPoints()).as("4 + 17 + 31").isEqualTo(52);
        assertThat(score.medal()).isEqualTo(MedalTier.GOLD);
        assertThat(score.medalPoints()).isEqualTo(45);
        assertThat(score.completionTime()).contains(completionTime);
        assertThat(score.total()).isEqualTo(97);
    }

    @Test
    void scoresAnUnfinishedRunAsRingPointsOnlyWithDidNotFinishAndNoCompletionTime() {
        // One of three rings passed: ringPoints must be that one ring's own 4, not the whole
        // course's 52 and not a flat 10.
        RingProgress unfinished = new RingProgress(1, -1);
        Duration timeOnCourse = Duration.ofSeconds(20);

        MapScore score = MapScorer.score(unfinished, THREE_RING_MAP, timeOnCourse);

        assertThat(score.ringPoints()).isEqualTo(4);
        assertThat(score.medal()).isEqualTo(MedalTier.DNF);
        assertThat(score.medalPoints()).isZero();
        assertThat(score.placementBonus()).isZero();
        // The 20 s is how long the player was on the course, not a completion time. Echoing it back
        // is what the old shape did, and it left every reader to check the medal first.
        assertThat(score.completionTime()).isEmpty();
        assertThat(score.total()).isEqualTo(4);
    }

    /**
     * Ring points are the passed rings' own values, summed. The flat 10 a ring that this replaced was
     * a regression against the tree being replaced, which reads {@code ring.points()} and only ever
     * writes 10 into it from its loader — so the seam was open there and welded shut here, with the
     * setup wizard due in E6 to let a map builder set the value.
     */
    @Test
    void awardsEachRingsOwnPointValueRatherThanAFlatRatePerRing() {
        // Two of three rings: 4 + 17. Not 2 x 10 (a flat rate), not 52 (the whole course), and not
        // 17 + 31 (the last two rings rather than the first two).
        MapScore twoOfThree = MapScorer.score(new RingProgress(2, -1), THREE_RING_MAP, Duration.ofSeconds(25));

        assertThat(twoOfThree.ringPoints()).isEqualTo(21);

        // The same passed count on the other map is a different number, because the rings are worth
        // different things: a scorer that had learned one map's values could not pass both.
        MapScore twoOfTwo = MapScorer.score(new RingProgress(2, -1), TWO_RING_MAP, Duration.ofSeconds(25));

        assertThat(twoOfTwo.ringPoints()).isEqualTo(34);
    }

    @Test
    void passingNoRingAtAllEarnsNoRingPoints() {
        MapScore none = MapScorer.score(new RingProgress(0, -1), THREE_RING_MAP, Duration.ofSeconds(25));

        assertThat(none.ringPoints()).isZero();
        assertThat(none.total()).isZero();
    }

    @Test
    void carriesAFinishersTimeThroughUnchangedAndReportsNoneForANonFinisher() {
        Duration oddDuration = Duration.ofSeconds(12).plusNanos(345);

        MapScore finishedScore = MapScorer.score(new RingProgress(2, -1), TWO_RING_MAP, oddDuration);
        MapScore unfinishedScore = MapScorer.score(new RingProgress(0, -1), TWO_RING_MAP, oddDuration);

        assertThat(finishedScore.completionTime()).contains(oddDuration);
        assertThat(unfinishedScore.completionTime()).as("the same elapsed value, deliberately not echoed back")
                .isEmpty();
    }
}
