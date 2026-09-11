package net.elytrarace.voyager.race.scoring;

import net.elytrarace.voyager.api.race.MedalTier;

import java.time.Duration;
import java.util.Optional;

/**
 * A player's score on a single map: ring points, medal points, and the placement bonus, kept as
 * three separate components rather than the old tree's single {@code positionBonus} field that
 * holds the medal points and then the placement bonus in sequence.
 *
 * <p>{@code completionTime} is empty exactly when the run did not finish — {@code medal()} is then
 * {@link MedalTier#DNF}. It is an {@link Optional} for the same reason {@link CupScore#bestTime()}
 * is: a run that never reached the last ring has no completion time, and saying so in the type is
 * the alternative to handing every reader a {@link Duration} that is only meaningful if they
 * remember to check the medal first. The two were decided differently one layer apart in the same
 * sitting; this is the layer that was wrong.
 *
 * <p>{@link MapScorer} always produces this with {@code placementBonus} zero — a later stage assigns
 * it once every player's run on the map is known.
 */
public record MapScore(int ringPoints, int medalPoints, int placementBonus,
        Optional<Duration> completionTime, MedalTier medal) {

    /** The sum of all three components. */
    public int total() {
        return ringPoints + medalPoints + placementBonus;
    }
}
