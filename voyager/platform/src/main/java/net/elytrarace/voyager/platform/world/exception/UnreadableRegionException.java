package net.elytrarace.voyager.platform.world.exception;

import java.nio.file.Path;

/**
 * Thrown when a region file of a world cannot be read chunk by chunk.
 *
 * <p>The message names the region file, because the deep check reports it to the operator and a
 * world name alone does not say which of its files is damaged. The original failure is the cause, so
 * the stack trace still shows what the reader did.
 */
public final class UnreadableRegionException extends RuntimeException {

    public UnreadableRegionException(String world, Path regionFile, Throwable cause) {
        super("region file %s of world '%s' could not be read: %s"
                .formatted(regionFile, world, cause.getMessage() == null ? cause.getClass().getName() : cause.getMessage()));
        initCause(cause);
    }
}
