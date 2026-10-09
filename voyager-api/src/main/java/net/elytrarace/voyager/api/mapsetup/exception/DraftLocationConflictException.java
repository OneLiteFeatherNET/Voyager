package net.elytrarace.voyager.api.mapsetup.exception;

import net.elytrarace.voyager.api.mapsetup.MapId;
import org.jetbrains.annotations.Nullable;

/**
 * Thrown when a draft is found in both folders, which a crash between a move and a delete can leave. The store never
 * chooses one of the two copies: either choice can lose the last action.
 */
public final class DraftLocationConflictException extends RuntimeException {

    private DraftLocationConflictException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }

    /**
     * @param id     the map's id
     * @param first  the path of one copy, as text
     * @param second the path of the other copy, as text
     */
    public static DraftLocationConflictException of(MapId id, String first,
            String second) {
        return new DraftLocationConflictException(
                "the draft '%s' exists twice, as %s and as %s; keep one copy, delete the other, and open the map again"
                        .formatted(id.value(), first, second), null);
    }
}
