package net.elytrarace.voyager.api.handoff.exception;

import java.util.UUID;

/**
 * Thrown when a racer cannot be handed to the hub: no handoff is installed, or the installed one refused the move. The
 * server answers it by disconnecting the racer with the translated message and no retry (ADR-0033, Q2).
 */
public final class HandoffUnavailableException extends RuntimeException {

    private HandoffUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @param playerId the racer whose move was refused
     * @param cause    why it was refused, or {@code null} when no handoff is installed at all
     * @return the exception to throw, naming the racer
     */
    public static HandoffUnavailableException refused(UUID playerId, Throwable cause) {
        return new HandoffUnavailableException(
                "the hub handoff for %s was refused".formatted(playerId), cause);
    }

    /**
     * @param playerId the racer who has no handoff to be moved by
     * @return the exception to throw, naming the racer
     */
    public static HandoffUnavailableException notInstalled(UUID playerId) {
        return new HandoffUnavailableException(
                "no hub handoff is installed, so %s cannot be moved".formatted(playerId), null);
    }
}
