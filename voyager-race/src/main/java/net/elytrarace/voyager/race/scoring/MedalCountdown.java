package net.elytrarace.voyager.race.scoring;

import net.elytrarace.voyager.api.race.MedalBrackets;
import net.elytrarace.voyager.api.race.MedalTier;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

import java.time.Duration;
import java.util.Optional;

/**
 * Turns a running race clock into the medal a racer can still finish on and the time left to keep it.
 *
 * <h2>Arithmetic, not estimation</h2>
 *
 * <p>Every number this produces is exact at the instant it is produced. The medal brackets are
 * multiples of a map's reference time, so the moment diamond becomes unreachable is a fixed point on
 * the clock — 60.000 s on a 60 s reference — and "36.6 s of diamond left" at elapsed 23.4 s is a
 * subtraction, not a forecast. Nothing here knows how fast the racer is going or how much course is
 * left, and it must not learn: a pace model on a course that climbs 373 blocks would be confidently
 * wrong, and a confidently wrong countdown is worse than none.
 *
 * <h2>It agrees with the scorer by construction</h2>
 *
 * <p>The tier comes from {@link MedalBrackets#classify}, the same call {@code MapScorer} makes when
 * the run actually ends. So the band the boss bar showed on the last tick of a race and the medal
 * the results screen awards cannot disagree — which they could if this re-derived the comparison.
 * {@link #boundaryOf} then answers the other half: the last elapsed time that still keeps that tier.
 */
@ApiStatus.Internal
public abstract class MedalCountdown {

    private MedalCountdown() {
    }

    /**
     * The best medal still reachable at {@code elapsed} on a map whose reference time is
     * {@code reference}, and how long is left before it is lost.
     *
     * @param elapsed the race clock, from {@code RaceClock.elapsed()}
     * @param reference the map's reference time
     * @param brackets the multipliers of {@code reference} that mark each band off
     */
    @Contract(pure = true)
    public static MedalOutlook outlook(Duration elapsed, Duration reference, MedalBrackets brackets) {
        MedalTier tier = brackets.classify(elapsed, reference);
        if (tier == MedalTier.FINISH) {
            return new MedalOutlook(MedalTier.FINISH, Optional.empty());
        }
        Duration boundary = boundaryOf(tier, reference, brackets);
        return new MedalOutlook(tier, Optional.of(boundary.minus(elapsed)));
    }

    /**
     * The last elapsed time that still classifies as {@code tier} — one nanosecond later is the band
     * below.
     *
     * <p>Truncating rather than rounding, because {@link MedalBrackets#classify} compares a
     * {@code long} of nanoseconds against the {@code double} product and keeps the tier while
     * {@code elapsed <= reference * multiplier}. The greatest {@code long} satisfying that is the
     * product truncated, so this is the same boundary the classifier uses and not a second opinion
     * about it.
     *
     * @throws IllegalArgumentException for {@link MedalTier#FINISH} and {@link MedalTier#DNF},
     *     neither of which is a bracketed band
     */
    @Contract(pure = true)
    public static Duration boundaryOf(MedalTier tier, Duration reference, MedalBrackets brackets) {
        double multiplier = switch (tier) {
            case DIAMOND -> brackets.diamond();
            case GOLD -> brackets.gold();
            case SILVER -> brackets.silver();
            case BRONZE -> brackets.bronze();
            case FINISH, DNF -> throw new IllegalArgumentException(
                    "%s is not one of the bracketed bands and has no boundary on the clock"
                            .formatted(tier));
        };
        return Duration.ofNanos((long) (reference.toNanos() * multiplier));
    }
}
