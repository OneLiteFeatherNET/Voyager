package net.elytrarace.voyager.platform.cup.exception;

import net.minestom.server.coordinate.Pos;

/**
 * Thrown when a racer cannot be moved onto a map because a chunk around its spawn failed to load.
 *
 * <p>The move is abandoned before the racer is touched, so they stay where they were. The cause is kept
 * so the stack trace still shows what the chunk reader reported.
 */
public final class MapArrivalException extends RuntimeException {

    public MapArrivalException(String world, Pos spawn, Throwable cause) {
        super("a racer could not arrive in world '%s' at %s: a chunk around the spawn failed to load: %s"
                .formatted(world, spawn, cause.getMessage() == null ? cause.getClass().getName() : cause.getMessage()));
        initCause(cause);
    }
}
