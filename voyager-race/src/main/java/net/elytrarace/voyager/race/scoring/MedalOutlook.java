package net.elytrarace.voyager.race.scoring;

import net.elytrarace.voyager.api.race.MedalTier;

import java.time.Duration;
import java.util.Optional;

/**
 * The best medal a run can still finish on, and how long is left before even that one is gone.
 *
 * <p>This is a statement about the clock, not a prediction about the racer: at elapsed 23.4 s on a
 * 60 s reference time, diamond is lost at 60.0 s, so the outlook is {@code DIAMOND} with 36.6 s left.
 * Nothing here models pace. A course that climbs 373 blocks has wildly non-linear splits, and a
 * linear estimate of where a racer will finish would be a lie told with a decimal point on it.
 *
 * @param tier the highest {@link MedalTier} the clock is still inside
 * @param untilLost how long until {@code tier} is lost; empty — and only — for
 *     {@link MedalTier#FINISH}, which is the band below every bracket and so has nothing under it
 *     to fall into
 */
public record MedalOutlook(MedalTier tier, Optional<Duration> untilLost) {

    public MedalOutlook {
        if (tier == MedalTier.DNF) {
            throw new IllegalArgumentException(
                    "DNF is what a run that never finished is scored as, not a band a running clock "
                            + "is inside");
        }
        if ((tier == MedalTier.FINISH) != untilLost.isEmpty()) {
            throw new IllegalArgumentException(
                    ("FINISH is the one tier with nothing left to lose and the only one without a "
                            + "countdown, was tier=%s untilLost=%s").formatted(tier, untilLost));
        }
        if (untilLost.isPresent() && untilLost.get().isNegative()) {
            throw new IllegalArgumentException(
                    "a band already lost is a different band, so the countdown must not be negative, was %s"
                            .formatted(untilLost.get()));
        }
    }
}
