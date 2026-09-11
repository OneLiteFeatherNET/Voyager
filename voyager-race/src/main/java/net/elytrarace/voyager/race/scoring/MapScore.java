package net.elytrarace.voyager.race.scoring;

import net.elytrarace.voyager.api.race.MedalTier;

import java.time.Duration;

/**
 * A player's score on a single map: ring points, medal points, and the placement bonus, kept as
 * three separate components rather than the old tree's single {@code positionBonus} field that
 * holds the medal points and then the placement bonus in sequence.
 *
 * <p>{@link MapScorer} always produces this with {@code placementBonus} zero — a later stage assigns
 * it once every player's run on the map is known.
 */
public record MapScore(int ringPoints, int medalPoints, int placementBonus, Duration completionTime,
        MedalTier medal) {

    /** The sum of all three components. */
    public int total() {
        return ringPoints + medalPoints + placementBonus;
    }
}
