package net.elytrarace.voyager.race.scoring;

import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.race.MedalTier;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlacementBonusTest {

    // Only total() matters to PlacementBonus, so ringPoints carries the whole total and every other
    // field is a fixed, unrelated value — nothing here should coincide with the bonus being tested.
    private static MapScore scoreOf(int total) {
        return new MapScore(total, 0, 0, Duration.ofSeconds(7), MedalTier.FINISH);
    }

    @Test
    void fourPlayersWithDistinctTotalsGetRankedBonusInDescendingOrder() {
        // Input order deliberately does not match rank order (40, 90, 65, 12 -> ranked 90/65/40/12):
        // a bonus that reads list position instead of rank would fail this immediately.
        List<MapScore> scores = List.of(scoreOf(40), scoreOf(90), scoreOf(65), scoreOf(12));

        List<MapScore> awarded = PlacementBonus.award(scores, GameMode.RACE);

        assertThat(awarded.get(0).placementBonus()).as("total 40, rank 3").isEqualTo(3);
        assertThat(awarded.get(1).placementBonus()).as("total 90, rank 1").isEqualTo(10);
        assertThat(awarded.get(2).placementBonus()).as("total 65, rank 2").isEqualTo(6);
        assertThat(awarded.get(3).placementBonus()).as("total 12, rank 4").isEqualTo(1);
    }

    @Test
    void fifthPlaceAlsoGetsOneBecauseTheTailIsFlatNotAContinuingSequence() {
        List<MapScore> scores = List.of(scoreOf(40), scoreOf(90), scoreOf(65), scoreOf(12), scoreOf(5));

        List<MapScore> awarded = PlacementBonus.award(scores, GameMode.RACE);

        assertThat(awarded.get(1).placementBonus()).as("total 90, rank 1").isEqualTo(10);
        assertThat(awarded.get(2).placementBonus()).as("total 65, rank 2").isEqualTo(6);
        assertThat(awarded.get(0).placementBonus()).as("total 40, rank 3").isEqualTo(3);
        // Ranks 4 and 5 both land on the flat tail bonus of 1 - not 2 then 1, which is what a bonus
        // that kept counting down past third place would produce.
        assertThat(awarded.get(3).placementBonus()).as("total 12, rank 4").isEqualTo(1);
        assertThat(awarded.get(4).placementBonus()).as("total 5, rank 5").isEqualTo(1);
    }

    @Test
    void practiceAwardsZeroToEveryoneIncludingTheLeader() {
        List<MapScore> scores = List.of(scoreOf(90), scoreOf(40), scoreOf(65), scoreOf(12), scoreOf(5));

        List<MapScore> awarded = PlacementBonus.award(scores, GameMode.PRACTICE);

        assertThat(awarded).allSatisfy(score -> assertThat(score.placementBonus()).isZero());
        // Spelled out separately per the brief: the would-be leader (total 90) gets nothing too.
        assertThat(awarded.get(0).placementBonus()).as("leader in PRACTICE").isZero();
    }

    @Test
    void tiedPlayersForFirstShareTheBonusAndTheNextDistinctTotalRanksBelowBothOfThem() {
        // Two players tied at the top (90, 90). Decision: equal totals get an equal bonus, and the
        // next distinct total (65) is ranked third - below BOTH tied players - rather than second,
        // because a rank counts distinct standings ahead of a score, not raw positions ahead of it.
        // The old tree sorts and indexes instead, so it would hand one of the tied 90s a 10 and the
        // other a 6 purely because of the sort's tie-break - that is an accident of implementation,
        // not a rule, so this test pins the chosen rule explicitly rather than leaving it to chance.
        List<MapScore> scores = List.of(scoreOf(40), scoreOf(90), scoreOf(65), scoreOf(90), scoreOf(12));

        List<MapScore> awarded = PlacementBonus.award(scores, GameMode.RACE);

        assertThat(awarded.get(1).placementBonus()).as("first 90, tied rank 1").isEqualTo(10);
        assertThat(awarded.get(3).placementBonus()).as("second 90, tied rank 1").isEqualTo(10);
        assertThat(awarded.get(2).placementBonus()).as("65, rank 3 (below both 90s, not rank 2)").isEqualTo(3);
        assertThat(awarded.get(0).placementBonus()).as("40, rank 4").isEqualTo(1);
        assertThat(awarded.get(4).placementBonus()).as("12, rank 5").isEqualTo(1);
    }

    @Test
    void doesNotMutateTheInputList() {
        List<MapScore> input = new ArrayList<>(
                List.of(scoreOf(40), scoreOf(90), scoreOf(65), scoreOf(90), scoreOf(12)));
        List<Integer> totalsBefore = input.stream().map(MapScore::total).toList();

        List<MapScore> awarded = PlacementBonus.award(input, GameMode.RACE);

        assertThat(input.stream().map(MapScore::total).toList()).as("input order/content unchanged")
                .isEqualTo(totalsBefore);
        assertThat(input).as("input's own placement bonuses stay untouched")
                .allSatisfy(score -> assertThat(score.placementBonus()).isZero());
        assertThat(awarded).as("result is a distinct list from the input").isNotSameAs(input);
    }
}
