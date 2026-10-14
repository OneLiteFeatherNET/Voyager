package net.elytrarace.voyager.persistence;

import java.time.Duration;

/** How long the writer waits before it tries the database again after a failed attempt. */
@FunctionalInterface
public interface Backoff {

    /**
     * @param failures the number of failed attempts in a row, one for the first failure
     * @return the wait before the next attempt, never negative
     */
    Duration delayAfter(int failures);
}
