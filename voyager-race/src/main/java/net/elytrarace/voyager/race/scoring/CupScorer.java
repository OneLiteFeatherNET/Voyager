package net.elytrarace.voyager.race.scoring;

import net.elytrarace.voyager.api.race.MedalTier;

import org.jetbrains.annotations.ApiStatus;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Sums a player's per-map scores into their standing across a whole cup.
 *
 * <p>A map the player did not finish ({@link MedalTier#DNF}) still contributes its ring points to
 * {@link CupScore#totalPoints()} — {@link MapScore#total()} counts them regardless — but its
 * {@link MapScore#completionTime()} is elapsed time on a run that never finished, not a real result,
 * so it is excluded from both {@link CupScore#bestTime()} and {@link CupScore#mapsFinished()}.
 */
@ApiStatus.Internal
public abstract class CupScorer {

    private CupScorer() {
    }

    /** Accumulates one player's {@code perMap} scores, one entry per map in the cup, into a {@link CupScore}. */
    public static CupScore accumulate(List<MapScore> perMap) {
        int totalPoints = 0;
        int mapsFinished = 0;
        Duration bestTime = null;
        for (MapScore score : perMap) {
            totalPoints += score.total();
            if (score.medal() == MedalTier.DNF) {
                continue;
            }
            mapsFinished++;
            if (bestTime == null || score.completionTime().compareTo(bestTime) < 0) {
                bestTime = score.completionTime();
            }
        }
        return new CupScore(totalPoints, Optional.ofNullable(bestTime), mapsFinished);
    }
}
