package net.elytrarace.voyager.platform.text.exception;

/**
 * Thrown when a map or cup display name uses a tag outside the colour, decoration and reset set, or shows more than the
 * visible character limit once its tags are removed.
 *
 * <p>The catalogue adapters catch it to name the file and the field in the message they refuse the file with, so the
 * operator reads which name is wrong and why.
 */
public final class InvalidDisplayNameException extends RuntimeException {

    private InvalidDisplayNameException(String message) {
        super(message);
    }

    /** A tag the display name may not use, such as {@code click} or {@code lang}. */
    public static InvalidDisplayNameException disallowedTag(String tag) {
        return new InvalidDisplayNameException(
                ("a display name may use only colour, decoration and reset tags, and '<%s>' is not one of them")
                        .formatted(tag));
    }

    /** A name whose visible text is longer than the limit. */
    public static InvalidDisplayNameException tooLong(int visible, int limit) {
        return new InvalidDisplayNameException(
                "a display name shows %d characters once its tags are removed, and the limit is %d"
                        .formatted(visible, limit));
    }
}
