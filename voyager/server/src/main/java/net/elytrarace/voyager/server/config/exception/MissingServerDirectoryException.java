package net.elytrarace.voyager.server.config.exception;

import java.nio.file.Path;
import java.util.Locale;

/**
 * A directory the server cannot start without is not there.
 *
 * <p>Names the absolute path and the property that sets it, in the same spirit as
 * {@code UnknownWorldException}: a relative path in the message is a message that means different
 * things depending on where the process was launched from, which is exactly the confusion a wrong
 * working directory produces in the first place.
 */
public final class MissingServerDirectoryException extends RuntimeException {

    /** What the data directory is called in a message, and the property that overrides it. */
    public static final String DATA = "data";

    /** What the worlds directory is called in a message, and the property that overrides it. */
    public static final String WORLDS = "worlds";

    private final String purpose;
    private final Path path;

    /**
     * @param purpose {@link #DATA} or {@link #WORLDS}
     * @param path the path that is not a directory, as configured
     */
    public MissingServerDirectoryException(String purpose, Path path) {
        super(message(purpose, path));
        this.purpose = purpose;
        this.path = path;
    }

    /**
     * One format string built in one place.
     *
     * <p>Written as a method rather than inline because {@code "a" + "b %s".formatted(x)} binds the
     * call to the last literal alone and leaves every placeholder in the first half unfilled — which
     * is what this constructor did until a test read the message back and found three raw
     * {@code %s}. A multi-line message with interpolation in it does not survive being assembled with
     * {@code +}.
     */
    private static String message(String purpose, Path path) {
        String systemProperty = "VOYAGER_%s_PATH".formatted(purpose.toUpperCase(Locale.ROOT));
        String gradleProperty = switch (purpose) {
            case DATA -> "dataPath";
            case WORLDS -> "worldsPath";
            default -> throw new IllegalArgumentException("unknown purpose '%s'".formatted(purpose));
        };
        return """
                the %s directory does not exist: %s \
                (set -D%s=<dir> (java -jar) or -P%s=<dir> (Gradle), \
                or run from the directory the relative default resolves against)"""
                .formatted(purpose, path.toAbsolutePath(), systemProperty, gradleProperty);
    }

    /** {@link #DATA} or {@link #WORLDS}. */
    public String purpose() {
        return purpose;
    }

    /** The configured path, as given — not resolved, so a caller can see what was actually asked for. */
    public Path path() {
        return path;
    }
}
