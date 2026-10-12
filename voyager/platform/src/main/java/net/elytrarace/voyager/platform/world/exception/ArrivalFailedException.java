package net.elytrarace.voyager.platform.world.exception;

import net.minestom.server.coordinate.Pos;

/**
 * Thrown when a racer cannot be moved into a world because a chunk around the spawn failed to load.
 *
 * <p>The move is abandoned before the racer is touched, so they stay where they were. The cause is kept so the stack
 * trace still shows what the chunk reader reported. The map and lobby transitions each rethrow it under their own name.
 */
public final class ArrivalFailedException extends RuntimeException {

    public ArrivalFailedException(String world, Pos spawn, Throwable cause) {
        super("a racer could not arrive in world '%s' at %s: a chunk around the spawn failed to load: %s"
                .formatted(world, spawn, cause.getMessage() == null ? cause.getClass().getName() : cause.getMessage()));
        initCause(cause);
    }
}
