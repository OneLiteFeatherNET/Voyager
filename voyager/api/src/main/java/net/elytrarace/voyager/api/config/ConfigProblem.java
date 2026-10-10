package net.elytrarace.voyager.api.config;

import java.util.Comparator;
import java.util.Objects;

/**
 * One thing wrong with the configuration, named by the file or setting it concerns and the field
 * inside it, so an operator can find it without reading the code.
 *
 * <p>Carries no I/O: the check that produces these lives in the platform and server modules. Only
 * the shape of a problem is API.
 *
 * @param key      the JSON field, or the setting name, the problem concerns; never blank
 * @param source   the absolute file path, or the setting the problem concerns; never blank
 * @param message  what is wrong, in words an operator can act on; never blank
 * @param severity whether the problem fails the check or is only reported
 */
public record ConfigProblem(String key, String source, String message, Severity severity) {

    /** Sorts by source, then key. A stable sort keeps two problems on the same pair in the order found. */
    public static final Comparator<ConfigProblem> ORDER =
            Comparator.comparing(ConfigProblem::source).thenComparing(ConfigProblem::key);

    /** Whether a problem fails the check. Only {@link #ERROR} does. */
    public enum Severity {
        /** Fails the check: the server would not run correctly, or the world would be wrong. */
        ERROR,
        /** Reported but does not fail the check. */
        WARNING
    }

    public ConfigProblem {
        requireText(key, "key");
        requireText(source, "source");
        requireText(message, "message");
        Objects.requireNonNull(severity, "severity must not be null");
    }

    /**
     * One line for the report: severity, source, key and message, in that order.
     *
     * @return the line an operator reads, without a trailing newline
     */
    public String format() {
        return "%s %s %s: %s".formatted(severity, source, key, message);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("%s must not be blank".formatted(field));
        }
    }
}
