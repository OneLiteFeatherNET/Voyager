package net.elytrarace.voyager.api.mapsetup.exception;

/** Thrown when a world folder name is not a plain name of the import root, such as {@code ..} or one with a separator. */
public final class InvalidWorldFolderNameException extends RuntimeException {

    private InvalidWorldFolderNameException(String message) {
        super(message);
    }

    public static InvalidWorldFolderNameException refused(String name) {
        return new InvalidWorldFolderNameException(
                "'%s' is not a world folder name: use letters, digits, '-', '_' and '.', at most 64 characters, "
                        .formatted(name) + "and not '.' or '..'");
    }
}
