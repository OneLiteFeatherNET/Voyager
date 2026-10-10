package net.elytrarace.voyager.setup.config;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.function.Function;

/**
 * Settings of the setup server. There is no configuration file; everything it reads is named here.
 *
 * <p>The two directories default to {@code run-setup/data} and {@code run-setup/worlds} under the project root, so the
 * setup server and the game server never share a directory by default. The game server's defaults are under
 * {@code run/}.
 *
 * @param host       the interface to bind
 * @param port       the port to bind; 25566 by default, so both servers can run on one developer machine
 * @param dataPath   the directory holding {@code maps/} and {@code drafts/}
 * @param worldsPath the directory holding one world folder per map
 */
public record SetupSettings(String host, int port, Path dataPath, Path worldsPath) {

    /** The system property of the data directory. */
    public static final String DATA_PATH_PROPERTY = "VOYAGER_DATA_PATH";
    /** The system property of the worlds directory. */
    public static final String WORLDS_PATH_PROPERTY = "VOYAGER_WORLDS_PATH";

    static final String DEFAULT_HOST = "0.0.0.0";
    static final int DEFAULT_PORT = 25566;
    static final String DEFAULT_DATA_PATH = "run-setup/data";
    static final String DEFAULT_WORLDS_PATH = "run-setup/worlds";

    public SetupSettings {
        if (host.isBlank()) {
            throw new IllegalArgumentException("host must not be blank");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535, was %d".formatted(port));
        }
    }

    /**
     * Reads the settings from the command line, {@code [host] [port]}, and the two system properties.
     *
     * @param args the command-line arguments, both optional
     * @return the settings
     * @throws IllegalArgumentException if the port is not a number in range
     */
    public static SetupSettings fromEnvironment(String[] args) {
        return fromProperties(args, System::getProperty);
    }

    /**
     * @param args     the command-line arguments, both optional
     * @param property the source of the two system properties; {@code null} for an unset one
     * @return the settings
     * @throws IllegalArgumentException if the port is not a number in range
     */
    public static SetupSettings fromProperties(String[] args, Function<String, @Nullable String> property) {
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int port = args.length > 1 ? parsePort(args[1]) : DEFAULT_PORT;
        return new SetupSettings(
                host,
                port,
                Path.of(valueOr(property.apply(DATA_PATH_PROPERTY), DEFAULT_DATA_PATH)),
                Path.of(valueOr(property.apply(WORLDS_PATH_PROPERTY), DEFAULT_WORLDS_PATH)));
    }

    private static int parsePort(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("port must be a number, was '%s'".formatted(text), exception);
        }
    }

    private static String valueOr(@Nullable String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
