package net.elytrarace.voyager.api.mapsetup.exception;

import net.elytrarace.voyager.api.mapsetup.MapId;

/**
 * Thrown when a guarded save finds that the stored copy of a draft is not the copy the change was made on, or that no
 * stored copy exists. Nothing is written, and the stored copy keeps its bytes.
 */
public final class DraftChangedException extends RuntimeException {

    private DraftChangedException(String message) {
        super(message);
    }

    public static DraftChangedException of(MapId id) {
        return new DraftChangedException(
                "the draft '%s' changed on disk since it was loaded, so the save was refused".formatted(id.value()));
    }
}
