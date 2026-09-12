package net.elytrarace.voyager.platform.text.exception;

/**
 * Thrown when the message bundle cannot be read, or holds a value this project's rendering path
 * cannot render.
 *
 * <p>The second case is the one worth a type. Placeholders here are MiniMessage's {@code <arg:0>};
 * the {@link java.text.MessageFormat} path is deliberately disabled, so a value written with
 * {@code {0}} does not fail — it renders the two braces and the digit into the middle of a sentence,
 * in front of a player, forever. Nothing downstream can tell that from text somebody meant. So it is
 * refused where the value is first read, with the key and the value in the message.
 */
public final class MalformedMessageBundleException extends RuntimeException {

    private MalformedMessageBundleException(String message) {
        super(message);
    }

    private MalformedMessageBundleException(String message, Throwable cause) {
        super(message, cause);
    }

    /** A value using MessageFormat's {@code {0}} where MiniMessage's {@code <arg:0>} belongs. */
    public static MalformedMessageBundleException messageFormatPlaceholder(String key, String value) {
        return new MalformedMessageBundleException(
                ("message '%s' uses MessageFormat placeholders, which render as literal text here — "
                        + "write <arg:0> instead of {0}; the value was '%s'").formatted(key, value));
    }

    /** The bundle exists but could not be read. */
    public static MalformedMessageBundleException unreadable(String resource, Throwable cause) {
        return new MalformedMessageBundleException(
                "the message bundle at '%s' could not be read".formatted(resource), cause);
    }
}
