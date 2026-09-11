package net.elytrarace.voyager.race.scoring;

import net.elytrarace.voyager.api.race.MedalTier;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CupScorerTest {

    @Test
    void sumsTotalPointsAcrossThreeMapsWithDifferentPerMapScores() {
        // Each map has a different ring/medal/placement mix so a scorer that only read one field, or
        // only the first/last map, could not pass by coincidence: 71 + 93 + 35.
        MapScore mapA = new MapScore(20, 45, 6, Optional.of(Duration.ofSeconds(50)), MedalTier.GOLD);
        MapScore mapB = new MapScore(30, 60, 3, Optional.of(Duration.ofSeconds(20)), MedalTier.DIAMOND);
        MapScore mapC = new MapScore(10, 15, 10, Optional.of(Duration.ofSeconds(80)), MedalTier.BRONZE);

        CupScore cup = CupScorer.accumulate(List.of(mapA, mapB, mapC));

        assertThat(cup.totalPoints()).isEqualTo(199);
        assertThat(cup.mapsFinished()).isEqualTo(3);
    }

    @Test
    void bestTimeIsTheSmallestCompletionTimeAcrossFinishedMapsNotTheFirstOrLastMap() {
        // The smallest time (20s) sits on the MIDDLE map, deliberately neither the first (50s) nor
        // the last (80s) map in the list - a scorer reading list().get(0) or the last entry instead
        // of the actual minimum would fail this either way.
        MapScore mapA = new MapScore(20, 45, 6, Optional.of(Duration.ofSeconds(50)), MedalTier.GOLD);
        MapScore mapB = new MapScore(30, 60, 3, Optional.of(Duration.ofSeconds(20)), MedalTier.DIAMOND);
        MapScore mapC = new MapScore(10, 15, 10, Optional.of(Duration.ofSeconds(80)), MedalTier.BRONZE);

        CupScore cup = CupScorer.accumulate(List.of(mapA, mapB, mapC));

        assertThat(cup.bestTime()).contains(Duration.ofSeconds(20));
    }

    @Test
    void aDnfMapContributesItsRingPointsButNotItsTimeAndDoesNotCountTowardMapsFinished() {
        // The DNF map's own completion time (1s) is the smallest of all three - if it were wrongly
        // folded into bestTime, or wrongly counted toward mapsFinished, this would show it. mapsFinished
        // (2) is deliberately not the list size (3), so a scorer that returned perMap.size() outright
        // would also be caught.
        MapScore finishedFirst = new MapScore(20, 45, 0, Optional.of(Duration.ofSeconds(40)), MedalTier.GOLD);
        // Deliberately inconsistent with what MapScorer produces: a DNF row carrying a present
        // time. MapScore's type says empty-exactly-when-DNF, so this row cannot come out of the
        // scorer — it exists to pin the DNF guard in CupScorer itself, which is the thing that
        // would have to fail for a non-result to reach a best time.
        MapScore dnf = new MapScore(10, 0, 0, Optional.of(Duration.ofSeconds(1)), MedalTier.DNF);
        MapScore finishedLast = new MapScore(30, 60, 0, Optional.of(Duration.ofSeconds(25)), MedalTier.DIAMOND);

        CupScore cup = CupScorer.accumulate(List.of(finishedFirst, dnf, finishedLast));

        assertThat(cup.totalPoints()).as("DNF map's ring points (10) still count").isEqualTo(165);
        assertThat(cup.mapsFinished()).isEqualTo(2);
        assertThat(cup.bestTime()).as("smallest time among the two FINISHED maps, not the DNF's 1s")
                .contains(Duration.ofSeconds(25));
    }

    @Test
    void aCupWhereEveryMapIsDnfHasZeroMapsFinishedAndNoBestTime() {
        MapScore dnf1 = new MapScore(10, 0, 0, Optional.empty(), MedalTier.DNF);
        MapScore dnf2 = new MapScore(20, 0, 0, Optional.empty(), MedalTier.DNF);
        MapScore dnf3 = new MapScore(0, 0, 0, Optional.empty(), MedalTier.DNF);

        CupScore cup = CupScorer.accumulate(List.of(dnf1, dnf2, dnf3));

        assertThat(cup.totalPoints()).as("ring points from every DNF still sum").isEqualTo(30);
        assertThat(cup.mapsFinished()).isZero();
        // Optional.empty() is the honest shape here - there is no time to report, not a sentinel
        // duration standing in for "none": the old tree uses -1 for this and has to remember what it
        // means at every read site.
        assertThat(cup.bestTime()).isEmpty();
    }
}
