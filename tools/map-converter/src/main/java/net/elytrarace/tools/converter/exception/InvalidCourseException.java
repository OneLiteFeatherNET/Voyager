package net.elytrarace.tools.converter.exception;

/**
 * Thrown when the old data cannot be converted into a racecourse without guessing.
 *
 * <p>Every use of this is a refusal rather than a fallback, and that is the point of the type. The
 * loader in the tree being replaced answered each of these questions with a default — an
 * unrecognised ring type became {@code STANDARD}, a degenerate rim pair became the normal
 * {@code (0, 0, 1)}, a missing centre became the first point in the list — and the result was a
 * course that loaded cleanly and pointed players the wrong way through two of its rings. A one-shot
 * conversion is the one moment where a wrong answer is cheap to catch and expensive to keep, so it
 * stops instead.
 */
public final class InvalidCourseException extends RuntimeException {

    public InvalidCourseException(String message) {
        super(message);
    }
}
