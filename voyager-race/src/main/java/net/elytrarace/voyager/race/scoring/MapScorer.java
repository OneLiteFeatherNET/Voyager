package net.elytrarace.voyager.race.scoring;

import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.MedalBrackets;
import net.elytrarace.voyager.api.race.MedalTier;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.race.progress.RingProgress;

import org.jetbrains.annotations.ApiStatus;

import java.time.Duration;
import java.util.Optional;

/**
 * Scores a player's run on a single map from how far they got and how long they took.
 *
 * <p>A player who passed every ring is classified against the map's own reference time; a player who
 * did not is a {@link MedalTier#DNF} regardless of elapsed time, and earns ring points only.
 *
 * <p>Ring points are each ring's own {@link Ring#points()}, summed over the rings the player
 * actually passed — not a flat rate per ring. That field is part of the content format a map builder
 * fills in, and a flat rate here would have made it a value the setup tooling collects and nothing
 * ever reads. The tree being replaced reads the per-ring value too; it is only its loader that
 * writes 10 into every ring, which is what made a flat rate look like the shipped behaviour.
 */
@ApiStatus.Internal
public abstract class MapScorer {

    private MapScorer() {
    }

    /**
     * Returns {@code progress}'s score on {@code map} for a run that took {@code elapsed}, with
     * {@code placementBonus} zero.
     *
     * <p>{@code elapsed} is how long the player was on the course, which for a run that did not
     * finish is simply how long the phase lasted. That is not a completion time, so the returned
     * score carries {@link Optional#empty()} rather than echoing it back.
     */
    public static MapScore score(RingProgress progress, MapDefinition map, Duration elapsed) {
        int ringPoints = pointsOfRingsPassed(progress.passedCount(), map);
        if (progress.passedCount() != map.rings().size()) {
            return new MapScore(ringPoints, MedalTier.DNF.medalPoints(), 0, Optional.empty(), MedalTier.DNF);
        }
        MedalTier medal = MedalBrackets.DEFAULT.classify(elapsed, map.referenceTime());
        return new MapScore(ringPoints, medal.medalPoints(), 0, Optional.of(elapsed), medal);
    }

    /**
     * The points of the first {@code passedCount} rings. Progression is strictly in order, so the
     * rings a player passed are exactly {@code rings[0 .. passedCount - 1]} — and
     * {@link MapDefinition} guarantees that list is indexed in that order.
     */
    private static int pointsOfRingsPassed(int passedCount, MapDefinition map) {
        int points = 0;
        for (int index = 0; index < passedCount; index++) {
            points += map.rings().get(index).points();
        }
        return points;
    }
}
