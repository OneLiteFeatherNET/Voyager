package net.elytrarace.voyager.persistence;

import java.time.Duration;
import java.util.Objects;

/**
 * The retry policy of the writer: the first failure waits {@code first}, each further failure in a row doubles
 * the wait, and no wait is longer than {@code cap}. The writer uses one second and sixty seconds.
 *
 * @param first the wait after the first failure, positive
 * @param cap   the longest wait, at least {@code first}
 */
public record ExponentialBackoff(Duration first, Duration cap) implements Backoff {

    private static final int MAX_DOUBLINGS = 30;

    public ExponentialBackoff {
        Objects.requireNonNull(first, "first delay must not be null");
        Objects.requireNonNull(cap, "cap must not be null");
        if (first.isZero() || first.isNegative()) {
            throw new IllegalArgumentException("first delay must be positive, was %s".formatted(first));
        }
        if (cap.compareTo(first) < 0) {
            throw new IllegalArgumentException("cap %s must not be below the first delay %s".formatted(cap, first));
        }
    }

    @Override
    public Duration delayAfter(int failures) {
        int doublings = Math.clamp(failures - 1L, 0, MAX_DOUBLINGS);
        Duration delay = first.multipliedBy(1L << doublings);
        return delay.compareTo(cap) > 0 ? cap : delay;
    }
}
