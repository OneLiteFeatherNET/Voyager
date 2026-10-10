package net.elytrarace.voyager.server.config;

import net.elytrarace.voyager.race.flow.RaceTimings;
import net.elytrarace.voyager.server.config.exception.MissingServerDirectoryException;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

/**
 * Everything the server needs before it can resolve a single object: where to listen, where the map
 * and cup catalogue is, where the worlds are, which cup to play, and whether this is a dev run.
 *
 * <h2>Where the data lives</h2>
 *
 * <p>Two system properties, {@code VOYAGER_DATA_PATH} and {@code VOYAGER_WORLDS_PATH}, defaulting to
 * the relative paths {@code run/data} and {@code run/worlds} — the same two names and the same two
 * defaults the tree being replaced uses. The Gradle run tasks set {@code workingDir} to the
 * repository's {@code run/} directory, so those relative defaults resolve to {@code run/run/data}
 * and {@code run/run/worlds}. <strong>The doubling is not a typo</strong>; it is where the one real
 * world already sits on a developer checkout, and a second convention in the same repository would
 * be worse than an inherited wart.
 *
 * <p>The catalogue is a directory of {@code .json} files, not jar entries:
 * {@code CatalogLoader} takes a {@link Path} and reads a directory, which
 * works from an unpacked distribution and not from inside a shaded jar. The build installs the
 * shipped catalogue into the data directory (see {@code prepareRunData} in this module's
 * {@code build.gradle.kts}); the server never writes there. A race server reads its map data and
 * never authors it, for the same reason {@code MapInstances} never saves a chunk.
 *
 * <h2>Both directories are checked here, not where they are first used</h2>
 *
 * <p>The compact constructor refuses a path that is not an existing directory, naming the absolute
 * path. A wrong data path is otherwise found by {@code UnreadableCatalogException} and a wrong
 * worlds path by {@code UnknownWorldException} — both good failures, but one of them arrives after
 * Minestom has initialised and the other only when a map is first entered. Resolving configuration
 * is the first thing this server does, so it is where "that directory is not there" belongs.
 *
 * @param host the interface to bind
 * @param port the port to bind
 * @param dataPath the directory holding {@code maps/} and {@code cups/}
 * @param worldsPath the directory holding one Anvil world directory per map world
 * @param cupName the cup to play, or empty to play the catalogue's only cup
 * @param devMode whether {@code voyager.dev} is set; shortens the lobby and registers the
 *     operator subcommands
 * @param minimumRacers how many racers must be online before a cup starts; at least one. Defaults
 *     to {@link #PRODUCTION_MINIMUM_RACERS}, or {@link #DEV_MINIMUM_RACERS} under dev mode
 */
public record ServerSettings(String host, int port, Path dataPath, Path worldsPath,
        Optional<String> cupName, boolean devMode, int minimumRacers) {

    /** The property naming the directory holding {@code maps/} and {@code cups/}. */
    public static final String DATA_PATH_PROPERTY = "VOYAGER_DATA_PATH";

    /** The property naming the directory holding one Anvil world directory per map world. */
    public static final String WORLDS_PATH_PROPERTY = "VOYAGER_WORLDS_PATH";

    /** The property naming the cup to play; absent means "the catalogue's only cup". */
    public static final String CUP_PROPERTY = "VOYAGER_CUP";

    /** The property that turns on dev mode. */
    public static final String DEV_MODE_PROPERTY = "voyager.dev";

    /** The property naming how many racers must be online before a cup starts; absent means the mode's default. */
    public static final String MIN_RACERS_PROPERTY = "VOYAGER_MIN_PLAYERS";

    /** The minimum racer count when {@link #MIN_RACERS_PROPERTY} is not set and dev mode is off. */
    public static final int PRODUCTION_MINIMUM_RACERS = 2;

    /** The minimum racer count when {@link #MIN_RACERS_PROPERTY} is not set and dev mode is on. */
    public static final int DEV_MINIMUM_RACERS = 1;

    /** The host bound when none is given. */
    static final String DEFAULT_HOST = "0.0.0.0";
    /** The port bound when none is given. */
    static final int DEFAULT_PORT = 25565;
    /** The data directory when {@link #DATA_PATH_PROPERTY} is not set. */
    static final String DEFAULT_DATA_PATH = "run/data";
    /** The worlds directory when {@link #WORLDS_PATH_PROPERTY} is not set. */
    static final String DEFAULT_WORLDS_PATH = "run/worlds";

    /**
     * The lobby, race and results lengths a production run uses: {@link RaceTimings#DEFAULT} — a
     * 20 s lobby, the unchanged 300 s race cap, 8 s of results between maps and 20 s after the last.
     */
    private static final RaceTimings PRODUCTION_TIMINGS = RaceTimings.DEFAULT;

    /**
     * A dev run's timings. The race length is unchanged — the committed course has a 60 s reference
     * time and a run that cannot be finished is not a shorter test, it is a different one. The lobby
     * and the results screen are what a debugging session spends its afternoon on, so those are the
     * two that shrink.
     */
    private static final RaceTimings DEV_TIMINGS = new RaceTimings(
            Duration.ofSeconds(10), PRODUCTION_TIMINGS.race(),
            Duration.ofSeconds(5), Duration.ofSeconds(10));

    /**
     * The settings with the minimum racer count the run's mode defaults to, for callers that do not set one.
     */
    public ServerSettings(String host, int port, Path dataPath, Path worldsPath, Optional<String> cupName,
            boolean devMode) {
        this(host, port, dataPath, worldsPath, cupName, devMode,
                devMode ? DEV_MINIMUM_RACERS : PRODUCTION_MINIMUM_RACERS);
    }

    public ServerSettings {
        if (host.isBlank()) {
            throw new IllegalArgumentException("host must not be blank");
        }
        requirePort(port);
        requireMinimumRacers(minimumRacers);
        requireDirectory(dataPath, MissingServerDirectoryException.DATA);
        requireDirectory(worldsPath, MissingServerDirectoryException.WORLDS);
    }

    /**
     * Reads the settings from {@code args} and the system properties.
     *
     * <p>{@code args} is {@code [host] [port]}, both optional, matching the tree being replaced's
     * command line so the same invocation works against either jar. A port that is not a number is
     * a refusal rather than a warning and a fallback: a server that silently binds 25565 when it was
     * told 25566 is a server two people are about to fight over.
     *
     * @throws MissingServerDirectoryException if either directory does not exist
     * @throws IllegalArgumentException if the port is not a number in range
     */
    public static ServerSettings fromEnvironment(String[] args) {
        return fromProperties(args, System::getProperty);
    }

    /**
     * {@link #fromEnvironment} with the properties supplied, so the configuration check can read the
     * same settings from a map in a test. Same defaults, same parsing, same refusals.
     */
    static ServerSettings fromProperties(String[] args, Function<String, @Nullable String> property) {
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int port = args.length > 1 ? parsePort(args[1]) : DEFAULT_PORT;
        boolean devMode = Boolean.parseBoolean(property.apply(DEV_MODE_PROPERTY));
        String minimum = blankToNull(property.apply(MIN_RACERS_PROPERTY));
        return new ServerSettings(
                host,
                port,
                Path.of(valueOr(property.apply(DATA_PATH_PROPERTY), DEFAULT_DATA_PATH)),
                Path.of(valueOr(property.apply(WORLDS_PATH_PROPERTY), DEFAULT_WORLDS_PATH)),
                Optional.ofNullable(blankToNull(property.apply(CUP_PROPERTY))),
                devMode,
                minimum == null
                        ? (devMode ? DEV_MINIMUM_RACERS : PRODUCTION_MINIMUM_RACERS)
                        : parseMinimumRacers(minimum));
    }

    /**
     * The phase lengths this run plays with: {@link RaceTimings#DEFAULT} normally, a short lobby and
     * a short results screen under {@code voyager.dev}.
     */
    public RaceTimings timings() {
        return devMode ? DEV_TIMINGS : PRODUCTION_TIMINGS;
    }

    /** One line naming every resolved value, for the boot log. Absolute paths, deliberately. */
    public String describe() {
        return "host=%s port=%s data=%s worlds=%s cup=%s dev=%s minimum=%s".formatted(
                host, port, dataPath.toAbsolutePath(), worldsPath.toAbsolutePath(),
                cupName.orElse("<the only one>"), devMode, minimumRacers);
    }

    /**
     * The port a command-line argument names.
     *
     * @throws IllegalArgumentException if the text is not a number
     */
    static int parsePort(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("port must be a number, was '%s'".formatted(raw), exception);
        }
    }

    /**
     * The minimum racer count a setting names.
     *
     * @throws IllegalArgumentException if the text is not a whole number
     */
    static int parseMinimumRacers(String raw) {
        try {
            return Integer.parseInt(raw.strip());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "%s must be a whole number, was '%s'".formatted(MIN_RACERS_PROPERTY, raw), exception);
        }
    }

    /**
     * The one range rule for the minimum racer count, shared by the constructor and the configuration check.
     *
     * @throws IllegalArgumentException if the count is below one
     */
    static void requireMinimumRacers(int minimumRacers) {
        if (minimumRacers < 1) {
            throw new IllegalArgumentException(
                    "%s must be at least 1, was %s".formatted(MIN_RACERS_PROPERTY, minimumRacers));
        }
    }

    /**
     * The one range rule for a port, shared by the constructor and the configuration check.
     *
     * @throws IllegalArgumentException if the port is outside 1..65535
     */
    static void requirePort(int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be in 1..65535, was %s".formatted(port));
        }
    }

    /**
     * Whether a directory the server needs is there: the one predicate both the constructor and the
     * configuration check ask, so they cannot disagree on what "there" means.
     */
    static boolean isDirectory(Path path) {
        return Files.isDirectory(path);
    }

    private static void requireDirectory(Path path, String purpose) {
        if (!isDirectory(path)) {
            throw new MissingServerDirectoryException(purpose, path);
        }
    }

    private static String valueOr(@Nullable String value, String fallback) {
        return value == null ? fallback : value;
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
