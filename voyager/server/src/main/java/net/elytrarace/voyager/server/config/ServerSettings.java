package net.elytrarace.voyager.server.config;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.LobbyDefinition;
import net.elytrarace.voyager.api.race.com.ComDifficulty;
import net.elytrarace.voyager.race.com.ComRoster;
import net.elytrarace.voyager.race.flow.RaceTimings;
import net.elytrarace.voyager.server.config.exception.InvalidSettingException;
import net.elytrarace.voyager.server.config.exception.MissingServerDirectoryException;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Everything the server needs before it can resolve a single object: where to listen, where the map
 * and cup catalogue is, where the worlds are, which cup to play, and whether this is a dev run.
 *
 * <h2>Where the data lives</h2>
 *
 * <p>Two system properties, {@code VOYAGER_DATA_PATH} and {@code VOYAGER_WORLDS_PATH}, default to the
 * relative paths {@code catalog} and {@code worlds}: the layout of a CloudNet service directory
 * (openspec change {@code add-cloudnet-deployment}, Decision 9). A service started from its template
 * therefore needs no property, as Cygnus's fixed relative paths need none. The Gradle run tasks set
 * {@code workingDir} to the repository's {@code run/} directory and pass {@code run/data} and
 * {@code run/worlds} explicitly, so a developer checkout resolves to {@code run/run/data} and
 * {@code run/run/worlds}. <strong>The doubling is not a typo</strong>; it is where the one real world
 * already sits on a developer checkout.
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
 * @param lobby         the world waiting racers stand in and its spawn, or empty when no lobby is configured, in
 *     which case waiting racers stand at the first map's spawn and the server warns at boot (ADR-0029)
 * @param maximumRacers how many racers make a full server; at least {@link #minimumRacers}. Defaults to
 *     {@link #DEFAULT_MAXIMUM_RACERS}. It is soft: it sets the join line and the full-lobby countdown, and refuses no one
 * @param lobbyHologram where the cup's hologram stands in the lobby world, or empty for no hologram. It needs a lobby
 *     (ADR-0033)
 * @param comCount how many computer racers fill the grid, humans included, from 0 to {@link ComRoster#MAXIMUM_COUNT}; 0 is
 *     the default and runs none
 * @param comDifficulty how hard every computer racer plays; {@link ComDifficulty#MEDIUM} when not set
 */
public record ServerSettings(String host, int port, Path dataPath, Path worldsPath,
        Optional<String> cupName, boolean devMode, int minimumRacers, Optional<LobbyDefinition> lobby,
        int maximumRacers, Optional<Vec3> lobbyHologram, int comCount, ComDifficulty comDifficulty) {

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

    /** The property naming the world directory waiting racers stand in; set together with {@link #LOBBY_SPAWN_PROPERTY}. */
    public static final String LOBBY_WORLD_PROPERTY = "VOYAGER_LOBBY_WORLD";

    /** The property naming the lobby's spawn as {@code x,y,z} in block coordinates; set together with {@link #LOBBY_WORLD_PROPERTY}. */
    public static final String LOBBY_SPAWN_PROPERTY = "VOYAGER_LOBBY_SPAWN";

    /** The property naming how many racers make a full server; absent means {@link #DEFAULT_MAXIMUM_RACERS}. */
    public static final String MAX_PLAYERS_PROPERTY = "VOYAGER_MAX_PLAYERS";

    /**
     * The property naming where the cup's hologram stands in the lobby, as {@code x,y,z} in block coordinates. Optional; it
     * needs {@link #LOBBY_WORLD_PROPERTY}, and it is refused without one.
     */
    public static final String LOBBY_HOLOGRAM_PROPERTY = "VOYAGER_LOBBY_HOLOGRAM";

    /** The maximum racer count when {@link #MAX_PLAYERS_PROPERTY} is not set, in production and in dev mode (ADR-0029). */
    public static final int DEFAULT_MAXIMUM_RACERS = 16;

    /** The property naming how many computer racers fill the grid; absent means {@code 0}, which runs none. */
    public static final String COM_COUNT_PROPERTY = "VOYAGER_COM_COUNT";

    /** The property naming how hard the computer racers play; absent means {@link ComDifficulty#MEDIUM}. */
    public static final String COM_DIFFICULTY_PROPERTY = "VOYAGER_COM_DIFFICULTY";

    /** The system property naming the interface to bind; read when no positional host is given. */
    public static final String BIND_HOST_PROPERTY = "service.bind.host";

    /** The system property naming the port to bind; read when no positional port is given. A node sets it. */
    public static final String BIND_PORT_PROPERTY = "service.bind.port";

    /**
     * Every key the settings read, so the configuration loader can hand the server one view of them for the configuration
     * check and the boot log.
     */
    public static final List<String> KEYS = List.of(DATA_PATH_PROPERTY, WORLDS_PATH_PROPERTY, CUP_PROPERTY,
            DEV_MODE_PROPERTY, MIN_RACERS_PROPERTY, LOBBY_WORLD_PROPERTY, LOBBY_SPAWN_PROPERTY, MAX_PLAYERS_PROPERTY,
            LOBBY_HOLOGRAM_PROPERTY, BIND_HOST_PROPERTY, BIND_PORT_PROPERTY, COM_COUNT_PROPERTY, COM_DIFFICULTY_PROPERTY);

    /** The minimum racer count when {@link #MIN_RACERS_PROPERTY} is not set and dev mode is off. */
    public static final int PRODUCTION_MINIMUM_RACERS = 2;

    /** The minimum racer count when {@link #MIN_RACERS_PROPERTY} is not set and dev mode is on. */
    public static final int DEV_MINIMUM_RACERS = 1;

    /** The host bound when none is given. */
    static final String DEFAULT_HOST = "0.0.0.0";
    /** The port bound when none is given. */
    static final int DEFAULT_PORT = 25565;
    /** The data directory when {@link #DATA_PATH_PROPERTY} is not set. */
    static final String DEFAULT_DATA_PATH = "catalog";
    /** The worlds directory when {@link #WORLDS_PATH_PROPERTY} is not set. */
    static final String DEFAULT_WORLDS_PATH = "worlds";

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

    /**
     * The settings with no lobby and the default maximum, for callers that do not configure either.
     */
    public ServerSettings(String host, int port, Path dataPath, Path worldsPath, Optional<String> cupName,
            boolean devMode, int minimumRacers) {
        this(host, port, dataPath, worldsPath, cupName, devMode, minimumRacers, Optional.empty(),
                DEFAULT_MAXIMUM_RACERS, Optional.empty());
    }

    /**
     * The settings with no computer racers and the default difficulty, for callers that do not configure either.
     */
    public ServerSettings(String host, int port, Path dataPath, Path worldsPath, Optional<String> cupName,
            boolean devMode, int minimumRacers, Optional<LobbyDefinition> lobby, int maximumRacers,
            Optional<Vec3> lobbyHologram) {
        this(host, port, dataPath, worldsPath, cupName, devMode, minimumRacers, lobby, maximumRacers, lobbyHologram,
                0, ComDifficulty.MEDIUM);
    }

    public ServerSettings {
        if (host.isBlank()) {
            throw new IllegalArgumentException("host must not be blank");
        }
        requirePort(port);
        requireMinimumRacers(minimumRacers);
        requireMaximumRacers(maximumRacers, minimumRacers);
        requireComCount(comCount);
        Objects.requireNonNull(comDifficulty, "comDifficulty");
        if (lobbyHologram.isPresent() && lobby.isEmpty()) {
            throw new InvalidSettingException(LOBBY_HOLOGRAM_PROPERTY, hologramNeedsLobby());
        }
        requireDirectory(dataPath, MissingServerDirectoryException.DATA);
        requireDirectory(worldsPath, MissingServerDirectoryException.WORLDS);
    }

    /**
     * Reads the settings from {@code args} and a property source. The composition root passes the configuration loader's
     * {@code get}, so the values come from every layer the loader reads (ADR-0036); a test passes a map.
     *
     * <p>{@code args} is {@code [host] [port]}, both optional, matching the tree being replaced's command line so the same
     * invocation works against either jar. Each of host and port takes the argument when one is given, then
     * {@link #BIND_HOST_PROPERTY} or {@link #BIND_PORT_PROPERTY}, then the default. A port that is not a number is a refusal
     * rather than a warning and a fallback: a server that silently binds 25565 when it was told 25566 is a server two people
     * are about to fight over.
     *
     * @throws MissingServerDirectoryException if either directory does not exist
     * @throws IllegalArgumentException if the port is not a number in range
     */
    public static ServerSettings fromProperties(String[] args, Function<String, @Nullable String> property) {
        String host = args.length > 0 ? args[0] : blankToDefault(property.apply(BIND_HOST_PROPERTY), DEFAULT_HOST);
        int port = args.length > 1 ? parsePort(args[1]) : bindPort(property.apply(BIND_PORT_PROPERTY));
        boolean devMode = Boolean.parseBoolean(property.apply(DEV_MODE_PROPERTY));
        int minimum = minimumRacersFrom(blankToNull(property.apply(MIN_RACERS_PROPERTY)), devMode);
        return new ServerSettings(
                host,
                port,
                Path.of(valueOr(property.apply(DATA_PATH_PROPERTY), DEFAULT_DATA_PATH)),
                Path.of(valueOr(property.apply(WORLDS_PATH_PROPERTY), DEFAULT_WORLDS_PATH)),
                Optional.ofNullable(blankToNull(property.apply(CUP_PROPERTY))),
                devMode,
                minimum,
                parseLobby(blankToNull(property.apply(LOBBY_WORLD_PROPERTY)),
                        blankToNull(property.apply(LOBBY_SPAWN_PROPERTY))),
                parseMaximumRacers(blankToNull(property.apply(MAX_PLAYERS_PROPERTY))),
                parseHologram(blankToNull(property.apply(LOBBY_HOLOGRAM_PROPERTY)),
                        blankToNull(property.apply(LOBBY_WORLD_PROPERTY))),
                parseComCount(blankToNull(property.apply(COM_COUNT_PROPERTY))),
                parseComDifficulty(blankToNull(property.apply(COM_DIFFICULTY_PROPERTY))));
    }

    /**
     * The computer racer count a setting names, or 0 when it is absent.
     *
     * @throws InvalidSettingException if the text is not a whole number, or is outside 0 to {@link ComRoster#MAXIMUM_COUNT};
     *     the message names {@link #COM_COUNT_PROPERTY}, the range and the text read
     */
    static int parseComCount(@Nullable String raw) {
        if (raw == null) {
            return 0;
        }
        int count;
        try {
            count = Integer.parseInt(raw.strip());
        } catch (NumberFormatException exception) {
            throw new InvalidSettingException(COM_COUNT_PROPERTY, "%s must be a whole number from 0 to %d, was '%s'"
                    .formatted(COM_COUNT_PROPERTY, ComRoster.MAXIMUM_COUNT, raw));
        }
        requireComCount(count);
        return count;
    }

    /**
     * The one range rule for the computer racer count, shared by the parser and the constructor.
     *
     * @throws InvalidSettingException if the count is outside 0 to {@link ComRoster#MAXIMUM_COUNT}
     */
    static void requireComCount(int count) {
        if (count < 0 || count > ComRoster.MAXIMUM_COUNT) {
            throw new InvalidSettingException(COM_COUNT_PROPERTY,
                    "%s must be from 0 to %d, was %d".formatted(COM_COUNT_PROPERTY, ComRoster.MAXIMUM_COUNT, count));
        }
    }

    /**
     * The difficulty a setting names, matched case-insensitively, or {@link ComDifficulty#MEDIUM} when it is absent.
     *
     * @throws InvalidSettingException if the text names none of EASY, MEDIUM or HARD; the message lists the three
     */
    static ComDifficulty parseComDifficulty(@Nullable String raw) {
        if (raw == null) {
            return ComDifficulty.MEDIUM;
        }
        return ComDifficulty.byName(raw).orElseThrow(() -> new InvalidSettingException(COM_DIFFICULTY_PROPERTY,
                "%s must be EASY, MEDIUM or HARD, was '%s'".formatted(COM_DIFFICULTY_PROPERTY, raw)));
    }

    /**
     * The minimum racer count a setting names, or the run's mode default when the setting is absent.
     *
     * @throws IllegalArgumentException if the text is not a whole number
     */
    static int minimumRacersFrom(@Nullable String raw, boolean devMode) {
        if (raw == null) {
            return devMode ? DEV_MINIMUM_RACERS : PRODUCTION_MINIMUM_RACERS;
        }
        return parseMinimumRacers(raw);
    }

    /**
     * The lobby a pair of settings names: both or neither. A world without a spawn, or a spawn without a world, is
     * refused naming the setting that is missing, because either one alone is a typo and not a fallback.
     *
     * @param world the world directory name, or {@code null} when the setting is absent
     * @param spawn the spawn as {@code x,y,z}, or {@code null} when the setting is absent
     * @return the lobby, or empty when neither setting is set
     * @throws InvalidSettingException if exactly one is set, or the spawn is not three finite numbers
     */
    static Optional<LobbyDefinition> parseLobby(@Nullable String world, @Nullable String spawn) {
        if (world == null && spawn == null) {
            return Optional.empty();
        }
        if (world == null) {
            throw new InvalidSettingException(LOBBY_WORLD_PROPERTY,
                    "%s is not set, but %s is: a lobby spawn needs the lobby world it stands in"
                            .formatted(LOBBY_WORLD_PROPERTY, LOBBY_SPAWN_PROPERTY));
        }
        if (spawn == null) {
            throw new InvalidSettingException(LOBBY_SPAWN_PROPERTY,
                    "%s is not set, but %s is: a lobby world needs the spawn racers stand on"
                            .formatted(LOBBY_SPAWN_PROPERTY, LOBBY_WORLD_PROPERTY));
        }
        return Optional.of(new LobbyDefinition(world.strip(), parseSpawn(spawn)));
    }

    /**
     * The hologram a pair of settings names: empty when the hologram is not set, and otherwise its position, which is refused
     * without a lobby world.
     *
     * @param raw   the position as {@code x,y,z}, or {@code null} when the setting is absent
     * @param world the lobby world directory name, or {@code null} when the setting is absent
     * @return the position, or empty when the hologram is not set
     * @throws InvalidSettingException if the position is not three finite numbers, or it is set without a lobby world; the
     *     message names {@link #LOBBY_HOLOGRAM_PROPERTY}
     */
    static Optional<Vec3> parseHologram(@Nullable String raw, @Nullable String world) {
        if (raw == null) {
            return Optional.empty();
        }
        Vec3 position = parseCoordinates(LOBBY_HOLOGRAM_PROPERTY, raw);
        if (world == null) {
            throw new InvalidSettingException(LOBBY_HOLOGRAM_PROPERTY, hologramNeedsLobby());
        }
        return Optional.of(position);
    }

    private static String hologramNeedsLobby() {
        return "%s is set, but %s is not: the hologram stands in the lobby world, so it needs %s"
                .formatted(LOBBY_HOLOGRAM_PROPERTY, LOBBY_WORLD_PROPERTY, LOBBY_WORLD_PROPERTY);
    }

    /**
     * The spawn a setting names: three finite numbers, {@code x,y,z}, in block coordinates.
     *
     * @throws InvalidSettingException if the text is not three finite numbers; the message names the text read
     */
    static Vec3 parseSpawn(String raw) {
        return parseCoordinates(LOBBY_SPAWN_PROPERTY, raw);
    }

    /**
     * Three finite numbers, {@code x,y,z}, in block coordinates, named by the setting they came from.
     *
     * @throws InvalidSettingException if the text is not three finite numbers; the message names the setting and the text
     */
    private static Vec3 parseCoordinates(String setting, String raw) {
        String[] parts = raw.split(",", -1);
        if (parts.length == 3) {
            try {
                double x = Double.parseDouble(parts[0].strip());
                double y = Double.parseDouble(parts[1].strip());
                double z = Double.parseDouble(parts[2].strip());
                if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)) {
                    return new Vec3(x, y, z);
                }
            } catch (NumberFormatException ignored) {
                // Reported below, once, with the text that was read.
            }
        }
        throw new InvalidSettingException(setting,
                "%s must be three numbers x,y,z in block coordinates, was '%s'".formatted(setting, raw));
    }

    /**
     * The maximum racer count a setting names, or {@link #DEFAULT_MAXIMUM_RACERS} when it is absent.
     *
     * @throws InvalidSettingException if the text is not a whole number
     */
    static int parseMaximumRacers(@Nullable String raw) {
        if (raw == null) {
            return DEFAULT_MAXIMUM_RACERS;
        }
        try {
            return Integer.parseInt(raw.strip());
        } catch (NumberFormatException exception) {
            throw new InvalidSettingException(MAX_PLAYERS_PROPERTY,
                    "%s must be a whole number, was '%s'".formatted(MAX_PLAYERS_PROPERTY, raw));
        }
    }

    /**
     * The one rule for the maximum racer count: it is not below the minimum. The refusal names both settings.
     *
     * @throws InvalidSettingException if {@code maximumRacers} is below {@code minimumRacers}
     */
    static void requireMaximumRacers(int maximumRacers, int minimumRacers) {
        if (maximumRacers < minimumRacers) {
            throw new InvalidSettingException(MAX_PLAYERS_PROPERTY,
                    "%s (%s) must not be below %s (%s)".formatted(
                            MAX_PLAYERS_PROPERTY, maximumRacers, MIN_RACERS_PROPERTY, minimumRacers));
        }
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
        return "host=%s port=%s data=%s worlds=%s cup=%s dev=%s minimum=%s maximum=%s lobby=%s hologram=%s".formatted(
                host, port, dataPath.toAbsolutePath(), worldsPath.toAbsolutePath(),
                cupName.orElse("<the only one>"), devMode, minimumRacers, maximumRacers,
                lobby.map(definition -> "%s@%s,%s,%s".formatted(definition.world(), definition.spawn().x(),
                        definition.spawn().y(), definition.spawn().z())).orElse("none"),
                lobbyHologram.map(at -> "%s,%s,%s".formatted(at.x(), at.y(), at.z())).orElse("none"));
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
     * The port the {@link #BIND_PORT_PROPERTY} names, or the default when it is absent or blank.
     *
     * @throws IllegalArgumentException if the text is not a number from 1 to 65535; the message names the property
     */
    static int bindPort(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_PORT;
        }
        int port;
        try {
            port = Integer.parseInt(raw.strip());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "%s must be a number, was '%s'".formatted(BIND_PORT_PROPERTY, raw), exception);
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("%s must be in 1..65535, was %d".formatted(BIND_PORT_PROPERTY, port));
        }
        return port;
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

    private static String blankToDefault(@Nullable String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
