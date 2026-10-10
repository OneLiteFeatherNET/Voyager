package net.elytrarace.voyager.race.scoring;

import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.race.MedalTier;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PlacementBonusTest {

    // Only total() matters to PlacementBonus, so ringPoints carries the whole total and every other
    // field is a fixed, unrelated value — nothing here should coincide with the bonus being tested.
    private static Placement<String> placement(String racer, int total) {
        return new Placement<>(racer,
                new MapScore(total, 0, 0, Optional.of(Duration.ofSeconds(7)), MedalTier.FINISH));
    }

    /**
     * Reads a result back the way a caller should: by the key it went in under. A bonus attached to
     * the wrong competitor is the failure this whole type exists to make impossible, and looking the
     * answer up by index would hide it.
     */
    private static int bonusOf(List<Placement<String>> awarded, String racer) {
        return awarded.stream()
                .filter(entry -> entry.key().equals(racer))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no placement for %s".formatted(racer)))
                .score()
                .placementBonus();
    }

    @Test
    void fourPlayersWithDistinctTotalsGetRankedBonusInDescendingOrder() {
        // Input order deliberately does not match rank order (40, 90, 65, 12 -> ranked 90/65/40/12):
        // a bonus that reads list position instead of rank would fail this immediately.
        List<Placement<String>> scores = List.of(placement("Wren", 40), placement("Rook", 90),
                placement("Pike", 65), placement("Tern", 12));

        List<Placement<String>> awarded = PlacementBonus.award(scores, GameMode.RACE);

        assertThat(bonusOf(awarded, "Rook")).as("total 90, rank 1").isEqualTo(10);
        assertThat(bonusOf(awarded, "Pike")).as("total 65, rank 2").isEqualTo(6);
        assertThat(bonusOf(awarded, "Wren")).as("total 40, rank 3").isEqualTo(3);
        assertThat(bonusOf(awarded, "Tern")).as("total 12, rank 4").isEqualTo(1);
    }

    /**
     * The positional contract, kept because callers that were written against it are still correct:
     * the result is the same size and in the same order as the input, entry for entry. What the key
     * adds is that nobody has to rely on that to find their own bonus.
     */
    @Test
    void resultsComeBackInInputOrderWithEveryKeyIntactAndTheScoreOtherwiseUnchanged() {
        List<Placement<String>> scores = List.of(placement("Wren", 40), placement("Rook", 90),
                placement("Pike", 65), placement("Tern", 12));

        List<Placement<String>> awarded = PlacementBonus.award(scores, GameMode.RACE);

        assertThat(awarded).extracting(Placement::key).containsExactly("Wren", "Rook", "Pike", "Tern");
        assertThat(awarded).extracting(entry -> entry.score().total() - entry.score().placementBonus())
                .as("every other component of the score is passed through untouched")
                .containsExactly(40, 90, 65, 12);
        assertThat(awarded).extracting(entry -> entry.score().completionTime())
                .allSatisfy(time -> assertThat(time).contains(Duration.ofSeconds(7)));
    }

    @Test
    void fifthPlaceAlsoGetsOneBecauseTheTailIsFlatNotAContinuingSequence() {
        List<Placement<String>> scores = List.of(placement("Wren", 40), placement("Rook", 90),
                placement("Pike", 65), placement("Tern", 12), placement("Auk", 5));

        List<Placement<String>> awarded = PlacementBonus.award(scores, GameMode.RACE);

        assertThat(bonusOf(awarded, "Rook")).as("total 90, rank 1").isEqualTo(10);
        assertThat(bonusOf(awarded, "Pike")).as("total 65, rank 2").isEqualTo(6);
        assertThat(bonusOf(awarded, "Wren")).as("total 40, rank 3").isEqualTo(3);
        // Ranks 4 and 5 both land on the flat tail bonus of 1 - not 2 then 1, which is what a bonus
        // that kept counting down past third place would produce.
        assertThat(bonusOf(awarded, "Tern")).as("total 12, rank 4").isEqualTo(1);
        assertThat(bonusOf(awarded, "Auk")).as("total 5, rank 5").isEqualTo(1);
    }

    @Test
    void practiceAwardsZeroToEveryoneIncludingTheLeader() {
        List<Placement<String>> scores = List.of(placement("Rook", 90), placement("Wren", 40),
                placement("Pike", 65), placement("Tern", 12), placement("Auk", 5));

        List<Placement<String>> awarded = PlacementBonus.award(scores, GameMode.PRACTICE);

        assertThat(awarded).allSatisfy(entry -> assertThat(entry.score().placementBonus()).isZero());
        // Spelled out separately per the brief: the would-be leader (total 90) gets nothing too.
        assertThat(bonusOf(awarded, "Rook")).as("leader in PRACTICE").isZero();
    }

    @Test
    void tiedPlayersForFirstShareTheBonusAndTheNextDistinctTotalRanksBelowBothOfThem() {
        // Two players tied at the top (90, 90). Decision: equal totals get an equal bonus, and the
        // next distinct total (65) is ranked third - below BOTH tied players - rather than second,
        // because a rank counts distinct standings ahead of a score, not raw positions ahead of it.
        // The old tree sorts and indexes instead, so it would hand one of the tied 90s a 10 and the
        // other a 6 purely because of the sort's tie-break - that is an accident of implementation,
        // not a rule, so this test pins the chosen rule explicitly rather than leaving it to chance.
        List<Placement<String>> scores = List.of(placement("Wren", 40), placement("Rook", 90),
                placement("Pike", 65), placement("Skua", 90), placement("Tern", 12));

        List<Placement<String>> awarded = PlacementBonus.award(scores, GameMode.RACE);

        assertThat(bonusOf(awarded, "Rook")).as("first 90, tied rank 1").isEqualTo(10);
        assertThat(bonusOf(awarded, "Skua")).as("second 90, tied rank 1").isEqualTo(10);
        assertThat(bonusOf(awarded, "Pike")).as("65, rank 3 (below both 90s, not rank 2)").isEqualTo(3);
        assertThat(bonusOf(awarded, "Wren")).as("40, rank 4").isEqualTo(1);
        assertThat(bonusOf(awarded, "Tern")).as("12, rank 5").isEqualTo(1);
    }

    /**
     * The javadoc promises each bonus is added on top of whatever the entry already carried, and
     * nothing tested it: every fixture and every production caller supplies a zero, so replacing
     * {@code placementBonus() + bonus} with a bare {@code bonus} survived the whole suite. A driver
     * re-scoring a map does exactly this — awards over an already-awarded list.
     */
    @Test
    void awardingTwiceAddsBothBonusesRatherThanOverwritingTheFirst() {
        List<Placement<String>> scores = List.of(placement("Wren", 40), placement("Rook", 90),
                placement("Pike", 65));

        List<Placement<String>> once = PlacementBonus.award(scores, GameMode.RACE);
        List<Placement<String>> twice = PlacementBonus.award(once, GameMode.RACE);

        // Ranks are unchanged by the first award here (10/6/3 preserves the 90/65/40 order), so the
        // second pass adds the same bonus again: overwrite semantics would leave 10/6/3.
        assertThat(bonusOf(twice, "Rook")).as("10 awarded twice").isEqualTo(20);
        assertThat(bonusOf(twice, "Pike")).as("6 awarded twice").isEqualTo(12);
        assertThat(bonusOf(twice, "Wren")).as("3 awarded twice").isEqualTo(6);
    }

    @Test
    void doesNotMutateTheInputList() {
        List<Placement<String>> input = new ArrayList<>(List.of(placement("Wren", 40), placement("Rook", 90),
                placement("Pike", 65), placement("Skua", 90), placement("Tern", 12)));
        List<Integer> totalsBefore = input.stream().map(entry -> entry.score().total()).toList();

        List<Placement<String>> awarded = PlacementBonus.award(input, GameMode.RACE);

        assertThat(input.stream().map(entry -> entry.score().total()).toList())
                .as("input order/content unchanged").isEqualTo(totalsBefore);
        assertThat(input).as("input's own placement bonuses stay untouched")
                .allSatisfy(entry -> assertThat(entry.score().placementBonus()).isZero());
        assertThat(awarded).as("result is a distinct list from the input").isNotSameAs(input);
    }
}
