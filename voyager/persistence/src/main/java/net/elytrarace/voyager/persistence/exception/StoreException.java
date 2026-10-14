package net.elytrarace.voyager.persistence.exception;

import org.jetbrains.annotations.Nullable;

/**
 * Thrown by a {@link net.elytrarace.voyager.persistence.CompletionStore} when a write fails. The SQLSTATE says
 * what kind of failure it was; a {@code null} state means the connection failed or the cause is unknown.
 */
public final class StoreException extends RuntimeException {

    @Nullable
    private final String sqlState;

    /**
     * @param message  what the store was doing, without any credential
     * @param sqlState the five-character SQLSTATE, or {@code null} when there is none
     * @param cause    the driver failure, or {@code null}
     */
    public StoreException(String message, @Nullable String sqlState, @Nullable Throwable cause) {
        super(message, cause);
        this.sqlState = sqlState;
    }

    /**
     * @return the SQLSTATE of the failure, or {@code null} when the connection failed or the state is unknown
     */
    @Nullable
    public String sqlState() {
        return sqlState;
    }
}
