package net.elytrarace.voyager.platform.text;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

import java.time.Duration;

/**
 * How a race time is written on a screen a racer is reading at 33 blocks a second.
 *
 * <h2>Two forms, and the difference is whether it is moving</h2>
 *
 * <ul>
 *   <li>{@link #tenths} — {@code M:SS.T}, for anything that is counting: the race clock and the
 *       medal countdown. Tenths because both refresh at 10 Hz and a tenths digit is the finest thing
 *       a player can read at a glance; hundredths at that cadence is a blur that looks like noise.</li>
 *   <li>{@link #whole} — {@code M:SS}, for a target that is not moving: a map's reference time, a
 *       recorded best. A stationary number with a tenth on it invites a comparison to a precision
 *       the number does not have.</li>
 * </ul>
 *
 * <h2>Truncated, never rounded</h2>
 *
 * <p>A clock shows the time that has passed, not the time it is about to be. At 0.97 s the race has
 * lasted 0.9 s and reads {@code 0:00.9}; rounding would show {@code 0:01.0} for a second that has not
 * happened, and a finish time is then a tenth faster than the run was. Both forms truncate for that
 * reason and for the same reason as each other.
 */
@ApiStatus.Internal
public abstract class RaceTimeFormat {

    private static final long MILLIS_PER_MINUTE = 60_000L;
    private static final long MILLIS_PER_SECOND = 1_000L;
    private static final long MILLIS_PER_TENTH = 100L;
    private static final long SECONDS_PER_MINUTE = 60L;
    private static final long TENTHS_PER_SECOND = 10L;

    private RaceTimeFormat() {
    }

    /** {@code M:SS.T} — a clock that is running. */
    @Contract(pure = true)
    public static String tenths(Duration duration) {
        long millis = nonNegativeMillis(duration);
        return "%d:%02d.%d".formatted(
                millis / MILLIS_PER_MINUTE,
                millis / MILLIS_PER_SECOND % SECONDS_PER_MINUTE,
                millis / MILLIS_PER_TENTH % TENTHS_PER_SECOND);
    }

    /** {@code M:SS} — a target that is standing still. */
    @Contract(pure = true)
    public static String whole(Duration duration) {
        long millis = nonNegativeMillis(duration);
        return "%d:%02d".formatted(
                millis / MILLIS_PER_MINUTE,
                millis / MILLIS_PER_SECOND % SECONDS_PER_MINUTE);
    }

    /**
     * A negative duration is refused rather than clamped or signed.
     *
     * <p>Nothing on a race screen counts below zero: the clock starts at the launch, and the medal
     * countdown stops existing the moment its band is lost rather than going negative. So a negative
     * value here is a caller that subtracted the wrong way round, and {@code -1:-3} printed under a
     * racer's crosshair is a defect that ships. This is a programming error, not a condition anybody
     * recovers from, so it is {@link IllegalArgumentException} and not a domain exception.
     */
    private static long nonNegativeMillis(Duration duration) {
        if (duration.isNegative()) {
            throw new IllegalArgumentException(
                    "a race clock does not run backwards, was %s".formatted(duration));
        }
        return duration.toMillis();
    }
}
