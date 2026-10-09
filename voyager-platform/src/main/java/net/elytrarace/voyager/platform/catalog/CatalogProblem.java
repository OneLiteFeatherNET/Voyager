package net.elytrarace.voyager.platform.catalog;

import java.nio.file.Path;

/**
 * One thing wrong with a data directory, as data rather than as a thrown exception.
 *
 * <p>{@code source} is the file or directory the problem concerns. {@code cause} is the exception the
 * loader would have thrown for it: the same type and the same message as before problems were
 * collected. Boot throws that cause; an operator report can print {@link #message()}.
 *
 * @param source the file, or the directory, the problem concerns
 * @param cause the exception describing the problem
 */
public record CatalogProblem(Path source, RuntimeException cause) {

    /** The cause's message, which is the text an operator reads. */
    public String message() {
        return cause.getMessage();
    }
}
