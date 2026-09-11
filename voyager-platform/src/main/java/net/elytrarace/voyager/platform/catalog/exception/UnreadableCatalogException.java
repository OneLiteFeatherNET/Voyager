package net.elytrarace.voyager.platform.catalog.exception;

import java.nio.file.Path;

/**
 * Thrown when a catalogue directory cannot be read, or holds nothing to read.
 *
 * <p>An empty directory counts, and that is the deliberate part. The overwhelmingly likely cause of
 * "the maps directory exists and contains no JSON" is a path that points somewhere almost right —
 * resources that were never unpacked, a working directory one level off. Left to pass, it produces a
 * catalogue that answers every lookup with an empty {@code Optional}: a server that starts, accepts
 * players, and has no map to put them on. Naming the directory at boot costs one line and rules that
 * out.
 */
public final class UnreadableCatalogException extends RuntimeException {

    public UnreadableCatalogException(String message) {
        super(message);
    }

    public UnreadableCatalogException(String message, Throwable cause) {
        super(message, cause);
    }

    public static UnreadableCatalogException notADirectory(String kind, Path directory) {
        return new UnreadableCatalogException("the %s catalogue directory %s does not exist".formatted(kind, directory));
    }

    public static UnreadableCatalogException empty(String kind, Path directory) {
        return new UnreadableCatalogException(
                ("the %s catalogue directory %s holds no .json file; a catalogue that answers every "
                        + "lookup with nothing is a wrong path far more often than it is an empty one")
                        .formatted(kind, directory));
    }
}
