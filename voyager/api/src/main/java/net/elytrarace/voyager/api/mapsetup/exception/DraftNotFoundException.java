package net.elytrarace.voyager.api.mapsetup.exception;

import net.elytrarace.voyager.api.mapsetup.MapId;
import org.jetbrains.annotations.Nullable;

/** Thrown when a draft is loaded for an id that no draft holds. */
public final class DraftNotFoundException extends RuntimeException {

    private DraftNotFoundException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }

    public static DraftNotFoundException of(MapId id) {
        return new DraftNotFoundException("no draft named '%s' exists".formatted(id.value()), null);
    }
}
