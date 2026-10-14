package net.elytrarace.voyager.api.stats.exception;

import org.jetbrains.annotations.Nullable;

/** Thrown when statistics cannot be read or written in time: the store is down, slow or refused the request. */
public final class StatsUnavailableException extends RuntimeException {

    public StatsUnavailableException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
