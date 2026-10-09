package net.elytrarace.voyager.api.mapsetup.exception;

import net.elytrarace.voyager.api.mapsetup.MapId;
import org.jetbrains.annotations.Nullable;

/** Thrown when a new map would take an id that a draft or a world already holds. */
public final class DraftAlreadyExistsException extends RuntimeException {

    private DraftAlreadyExistsException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }

    public static DraftAlreadyExistsException of(MapId id) {
        return new DraftAlreadyExistsException(
                "a draft or a world named '%s' already exists".formatted(id.value()), null);
    }
}
