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
 * {@link CupScore#totalPoints()} — {@link MapScore#total()} counts them regardless — but it has no
 * completion time at all, so it counts toward neither {@link CupScore#bestTime()} nor
 * {@link CupScore#mapsFinished()}. The {@code DNF} check below is what excludes it from
 * {@code mapsFinished}; {@code bestTime} no longer needs one, because
 * {@link MapScore#completionTime()} is empty on exactly those rows.
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
            Optional<Duration> completed = score.completionTime();
            if (completed.isPresent() && (bestTime == null || completed.get().compareTo(bestTime) < 0)) {
                bestTime = completed.get();
            }
        }
        return new CupScore(totalPoints, Optional.ofNullable(bestTime), mapsFinished);
    }
}
