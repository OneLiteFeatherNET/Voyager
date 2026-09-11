package net.elytrarace.voyager.race.scoring;

import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.MedalBrackets;
import net.elytrarace.voyager.api.race.MedalTier;
import net.elytrarace.voyager.race.progress.RingProgress;

import org.jetbrains.annotations.ApiStatus;

import java.time.Duration;

/**
 * Scores a player's run on a single map from how far they got and how long they took.
 *
 * <p>A player who passed every ring is classified against the map's own reference time; a player who
 * did not is a {@link MedalTier#DNF} regardless of elapsed time, and earns ring points only.
 */
@ApiStatus.Internal
public abstract class MapScorer {

    private static final int POINTS_PER_RING = 10;

    private MapScorer() {
    }

    /**
     * Returns {@code progress}'s score on {@code map} for a run that took {@code elapsed}, with
     * {@code placementBonus} zero.
     */
    public static MapScore score(RingProgress progress, MapDefinition map, Duration elapsed) {
        int ringPoints = progress.passedCount() * POINTS_PER_RING;
        if (progress.passedCount() != map.rings().size()) {
            return new MapScore(ringPoints, MedalTier.DNF.medalPoints(), 0, elapsed, MedalTier.DNF);
        }
        MedalTier medal = MedalBrackets.DEFAULT.classify(elapsed, map.referenceTime());
        return new MapScore(ringPoints, medal.medalPoints(), 0, elapsed, medal);
    }
}
