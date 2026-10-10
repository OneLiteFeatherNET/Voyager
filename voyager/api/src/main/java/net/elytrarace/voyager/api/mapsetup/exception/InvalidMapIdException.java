package net.elytrarace.voyager.api.mapsetup.exception;

import org.jetbrains.annotations.Nullable;

/** Thrown when a map id is not a safe folder name; refused before the file system is touched. */
public final class InvalidMapIdException extends RuntimeException {

    private InvalidMapIdException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }

    public static InvalidMapIdException refused(String value) {
        return new InvalidMapIdException(
                "map id '%s' must match [a-z0-9][a-z0-9_-]{0,31}, the rule for a folder name".formatted(value), null);
    }
}
