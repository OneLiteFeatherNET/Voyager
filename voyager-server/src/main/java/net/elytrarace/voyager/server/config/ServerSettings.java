package net.elytrarace.voyager.server.config;

import net.elytrarace.voyager.race.flow.RaceTimings;
import net.elytrarace.voyager.server.config.exception.MissingServerDirectoryException;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

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
 * {@code JsonMapCatalog} and {@code JsonCupCatalog} take a {@link Path} and read a directory, which
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
 */
public record ServerSettings(String host, int port, Path dataPath, Path worldsPath,
        Optional<String> cupName, boolean devMode) {

    /** The property naming the directory holding {@code maps/} and {@code cups/}. */
    public static final String DATA_PATH_PROPERTY = "VOYAGER_DATA_PATH";

    /** The property naming the directory holding one Anvil world directory per map world. */
    public static final String WORLDS_PATH_PROPERTY = "VOYAGER_WORLDS_PATH";

    /** The property naming the cup to play; absent means "the catalogue's only cup". */
    public static final String CUP_PROPERTY = "VOYAGER_CUP";

    /** The property that turns on dev mode. */
    public static final String DEV_MODE_PROPERTY = "voyager.dev";

    private static final String DEFAULT_HOST = "0.0.0.0";
    private static final int DEFAULT_PORT = 25565;
    private static final String DEFAULT_DATA_PATH = "run/data";
    private static final String DEFAULT_WORLDS_PATH = "run/worlds";

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

    public ServerSettings {
        if (host.isBlank()) {
            throw new IllegalArgumentException("host must not be blank");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be in 1..65535, was %s".formatted(port));
        }
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
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int port = args.length > 1 ? parsePort(args[1]) : DEFAULT_PORT;
        return new ServerSettings(
                host,
                port,
                Path.of(System.getProperty(DATA_PATH_PROPERTY, DEFAULT_DATA_PATH)),
                Path.of(System.getProperty(WORLDS_PATH_PROPERTY, DEFAULT_WORLDS_PATH)),
                Optional.ofNullable(blankToNull(System.getProperty(CUP_PROPERTY))),
                Boolean.getBoolean(DEV_MODE_PROPERTY));
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
        return "host=%s port=%s data=%s worlds=%s cup=%s dev=%s".formatted(
                host, port, dataPath.toAbsolutePath(), worldsPath.toAbsolutePath(),
                cupName.orElse("<the only one>"), devMode);
    }

    private static int parsePort(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("port must be a number, was '%s'".formatted(raw), exception);
        }
    }

    private static void requireDirectory(Path path, String purpose) {
        if (!Files.isDirectory(path)) {
            throw new MissingServerDirectoryException(purpose, path);
        }
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
