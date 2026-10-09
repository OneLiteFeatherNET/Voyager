package net.elytrarace.voyager.api.mapsetup.exception;

import net.elytrarace.voyager.api.mapsetup.MapId;
import org.jetbrains.annotations.Nullable;

/** Thrown when a draft breaks an invariant of its content. */
public final class InvalidDraftException extends RuntimeException {

    private InvalidDraftException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }

    public static InvalidDraftException blankWorld(MapId id) {
        return new InvalidDraftException("draft '%s' names no world".formatted(id.value()), null);
    }

    public static InvalidDraftException ringOutOfPlace(MapId id, int position, int index) {
        return new InvalidDraftException(
                "draft '%s' holds ring index %d at position %d; an index must equal its position".formatted(id.value(), index, position),
                null);
    }

    public static InvalidDraftException nonPositiveReferenceTime(MapId id, double seconds) {
        return new InvalidDraftException(
                "draft '%s' has a reference time of %s seconds; it must be finite and positive".formatted(id.value(), seconds),
                null);
    }
}
