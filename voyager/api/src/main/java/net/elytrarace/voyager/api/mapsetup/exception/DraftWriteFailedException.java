package net.elytrarace.voyager.api.mapsetup.exception;

import net.elytrarace.voyager.api.mapsetup.MapId;
import org.jetbrains.annotations.Nullable;

/** Thrown when a save did not complete; the previous stored copy is unchanged. */
public final class DraftWriteFailedException extends RuntimeException {

    private DraftWriteFailedException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }

    public static DraftWriteFailedException of(MapId id, Throwable cause) {
        return new DraftWriteFailedException(
                "the draft '%s' could not be written: %s".formatted(id.value(), cause.getMessage()), cause);
    }
}
