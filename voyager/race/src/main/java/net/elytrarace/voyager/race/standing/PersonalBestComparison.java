package net.elytrarace.voyager.race.standing;

import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/**
 * A finish compared with the stored best of the same map, layout and mode.
 *
 * <p>The comparison is on the millisecond the store keeps. A first run has no best to measure from, so its delta is zero
 * and nothing is said about one.
 *
 * @param outcome how the finish stands against the best
 * @param delta the time between the finish and the best; zero unless the outcome is {@link Outcome#NEW_BEST} or
 *     {@link Outcome#SLOWER}
 */
public record PersonalBestComparison(Outcome outcome, Duration delta) {

    /** How a finish stands against the stored best. */
    public enum Outcome {
        /** The finish beats the stored best. */
        NEW_BEST,
        /** The finish is slower than the stored best. */
        SLOWER,
        /** The finish equals the stored best. */
        EQUAL,
        /** No best is stored for the map yet. */
        FIRST_RUN
    }

    /**
     * @param outcome how the finish stands against the best
     * @param delta the time between the finish and the best, never negative
     */
    public PersonalBestComparison {
        if (delta.isNegative()) {
            throw new IllegalArgumentException("a delta to the best cannot be negative, was %s".formatted(delta));
        }
    }

    /**
     * Compares a finish with the stored best.
     *
     * @param finish the racer's finish on the map
     * @param best the stored best, or {@code null} when none is stored
     * @return the comparison
     */
    public static PersonalBestComparison of(Duration finish, @Nullable Duration best) {
        if (best == null) {
            return new PersonalBestComparison(Outcome.FIRST_RUN, Duration.ZERO);
        }
        int order = finish.compareTo(best);
        if (order < 0) {
            return new PersonalBestComparison(Outcome.NEW_BEST, best.minus(finish));
        }
        if (order > 0) {
            return new PersonalBestComparison(Outcome.SLOWER, finish.minus(best));
        }
        return new PersonalBestComparison(Outcome.EQUAL, Duration.ZERO);
    }
}
