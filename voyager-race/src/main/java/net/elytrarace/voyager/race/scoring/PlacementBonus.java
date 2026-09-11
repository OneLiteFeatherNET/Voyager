package net.elytrarace.voyager.race.scoring;

import net.elytrarace.voyager.api.race.GameMode;

import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Awards each player's placement bonus for their standing on a single map, ranked by {@link
 * MapScore#total()} descending: 1st place 10, 2nd 6, 3rd 3, every rank below that flat at 1 — the
 * tail does not keep counting down.
 *
 * <p>Two players tied on {@code total()} share the same rank and therefore the same bonus; the next
 * distinct total is ranked below <em>both</em> of them, not below just one of them — a tie for first
 * is followed by third place, never second. This is new work (D-E3-10): the old tree instead sorts
 * the field and awards by list index, so which of two tied players lands on 10 and which on 6 is
 * decided by whatever the sort's tie-break happens to do, not by a rule anyone chose. This rebuild
 * pins the rule instead of leaving it to the sort.
 *
 * <p>Awarded only when {@link GameMode#ranked()} — {@code PRACTICE} does not chase placement, so
 * every player there gets zero, including whoever scored highest.
 */
@ApiStatus.Internal
public abstract class PlacementBonus {

    private static final int FIRST_PLACE_BONUS = 10;
    private static final int SECOND_PLACE_BONUS = 6;
    private static final int THIRD_PLACE_BONUS = 3;
    private static final int PARTICIPATION_BONUS = 1;

    private PlacementBonus() {
    }

    /**
     * Returns a new list, the same size and in the same order as {@code scores}, with each entry's
     * placement bonus added on top of whatever it already carried. {@code scores} itself is not
     * mutated: this returns replacement {@link MapScore} values rather than editing in place, and
     * never reorders or sorts the input.
     */
    public static List<MapScore> award(List<MapScore> scores, GameMode mode) {
        int[] totals = new int[scores.size()];
        for (int i = 0; i < scores.size(); i++) {
            totals[i] = scores.get(i).total();
        }

        List<MapScore> awarded = new ArrayList<>(scores.size());
        for (int i = 0; i < scores.size(); i++) {
            int bonus = mode.ranked() ? bonusFor(rankOf(totals, i)) : 0;
            MapScore score = scores.get(i);
            awarded.add(new MapScore(score.ringPoints(), score.medalPoints(), score.placementBonus() + bonus,
                    score.completionTime(), score.medal()));
        }
        return List.copyOf(awarded);
    }

    /**
     * The 1-based rank of {@code totals[index]}: one more than the number of entries strictly
     * greater than it. Entries with an equal total land on the same rank, so a total beaten by two
     * higher-but-equal totals is ranked third, not second — the rank counts distinct standings ahead
     * of it, not positions ahead of it.
     */
    private static int rankOf(int[] totals, int index) {
        int rank = 1;
        int own = totals[index];
        for (int total : totals) {
            if (total > own) {
                rank++;
            }
        }
        return rank;
    }

    private static int bonusFor(int rank) {
        return switch (rank) {
            case 1 -> FIRST_PLACE_BONUS;
            case 2 -> SECOND_PLACE_BONUS;
            case 3 -> THIRD_PLACE_BONUS;
            default -> PARTICIPATION_BONUS;
        };
    }
}
