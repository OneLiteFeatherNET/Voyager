package net.elytrarace.voyager.platform.catalog.exception;

import java.nio.file.Path;

/**
 * Thrown when one file in a catalogue directory is not the definition it claims to be.
 *
 * <p>The message names the file, which is the entire reason this type exists rather than letting
 * Gson's own {@code JsonParseException} escape. A malformed map surfaces at boot as one line an
 * operator can act on, instead of as a missing ring somewhere in the middle of a rotation.
 *
 * <p>It wraps both kinds of failure — JSON that will not parse, and JSON that parses into values a
 * {@code MapDefinition} or {@code CupDefinition} refuses — because from the operator's side they are
 * the same event: this file is wrong, and here is what is wrong with it.
 */
public final class MalformedCatalogFileException extends RuntimeException {

    public MalformedCatalogFileException(Path file, Throwable cause) {
        super("%s is not a valid definition: %s".formatted(file, cause.getMessage()), cause);
    }
}
