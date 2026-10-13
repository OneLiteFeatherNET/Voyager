package net.elytrarace.voyager.server.config;

import net.elytrarace.voyager.api.config.ConfigProblem;
import net.elytrarace.voyager.api.config.ConfigProblem.Severity;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.LobbyDefinition;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.platform.catalog.CatalogLoader;
import net.elytrarace.voyager.platform.config.ConfigLoader;
import net.elytrarace.voyager.platform.catalog.CatalogReading;
import net.elytrarace.voyager.platform.catalog.CatalogValidation;
import net.elytrarace.voyager.platform.catalog.CupResolution;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.platform.world.WorldFolders;
import net.elytrarace.voyager.platform.world.WorldHealth;
import net.elytrarace.voyager.server.config.exception.InvalidSettingException;
import net.elytrarace.voyager.server.config.exception.MissingServerDirectoryException;

import net.minestom.server.instance.InstanceManager;

import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The configuration check: every problem with the settings, the catalogue and the worlds, collected
 * before the server starts, and reported with its file and field.
 *
 * <p>Two triggers share this code. {@code -Dvoyager.config.check=true} runs the check and exits, and
 * a normal boot runs the same check and refuses with the same report before it builds the object
 * graph, so an operator sees every error at once rather than one per restart.
 *
 * <p>The settings half does not throw. {@link ServerSettings}' constructor still refuses on the
 * library path; this class asks the same questions through the same predicates and collects the
 * answers instead.
 */
public final class ConfigCheck {

    /** The system property that turns the check into a validate-and-exit run. */
    public static final String CHECK_PROPERTY = "voyager.config.check";

    private static final String PORT_KEY = "port";
    private static final String PORT_SOURCE = "command line";
    /** The label of a setting named by a system property, which is where every setting came from before the loader. */
    public static final Function<String, String> PROPERTY_SOURCE = key -> "system property " + key;

    /**
     * The labels of the configuration loader's sources. A key that is set is labelled with the source that set it (system
     * property, environment variable, or file); a key that is not set keeps the label it always had, the system property
     * it would be set by, so the report for an unset key is the one the release before the loader printed.
     *
     * @param loader the loader of the running server
     * @return the label of each key
     */
    public static Function<String, String> sourcesOf(ConfigLoader loader) {
        return key -> loader.get(key) == null ? PROPERTY_SOURCE.apply(key) : loader.sourceOf(key);
    }

    private ConfigCheck() {
    }

    /**
     * The settings that cannot be used, each as one problem, without stopping at the first.
     *
     * @param args       the command line, {@code [host] [port]}
     * @param properties the system properties the settings read, by name
     * @return every problem with the settings; empty when {@link ServerSettings#fromProperties} would accept them
     */
    public static List<ConfigProblem> settingsProblems(String[] args, Map<String, String> properties) {
        return settingsProblems(args, properties, PROPERTY_SOURCE);
    }

    /**
     * {@link #settingsProblems(String[], Map)} with each problem labelled by {@code sources}, which names the source of a
     * setting the way the configuration loader knows it.
     *
     * @param sources the label of the source of a key, such as {@code system property VOYAGER_CUP}
     */
    public static List<ConfigProblem> settingsProblems(String[] args, Map<String, String> properties,
            Function<String, String> sources) {
        List<ConfigProblem> problems = new ArrayList<>();
        if (args.length > 0 && args[0].isBlank()) {
            problems.add(error("host", PORT_SOURCE, "host must not be blank"));
        }
        if (args.length > 1) {
            try {
                ServerSettings.requirePort(ServerSettings.parsePort(args[1]));
            } catch (IllegalArgumentException exception) {
                problems.add(error(PORT_KEY, PORT_SOURCE, exception.getMessage()));
            }
        }
        if (args.length <= 1) {
            bindPortProblem(problems, properties, sources);
        }
        directoryProblem(problems, properties, ServerSettings.DATA_PATH_PROPERTY,
                ServerSettings.DEFAULT_DATA_PATH, MissingServerDirectoryException.DATA);
        directoryProblem(problems, properties, ServerSettings.WORLDS_PATH_PROPERTY,
                ServerSettings.DEFAULT_WORLDS_PATH, MissingServerDirectoryException.WORLDS);
        minimumRacersProblem(problems, properties, sources);
        lobbySettingProblems(problems, properties, sources);
        problems.sort(ConfigProblem.ORDER);
        return List.copyOf(problems);
    }

    /**
     * The settings for a configuration whose {@link #settingsProblems} are empty.
     *
     * @throws IllegalStateException if the settings were not checked first and are refused
     */
    public static ServerSettings settingsOf(String[] args, Map<String, String> properties) {
        try {
            return ServerSettings.fromProperties(args, properties::get);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "the settings were refused after the check passed them: %s".formatted(exception.getMessage()),
                    exception);
        }
    }

    /**
     * The cup selection, checked the way the server will resolve it: a name that matches no cup, or no name
     * with a catalogue that holds more than one cup file. A cup file that does not parse, or a duplicate
     * name, is not repeated here; {@link CatalogValidation} already reports it as its own problem.
     *
     * @param settings settings whose data directory is present
     * @return one problem when the selection cannot be resolved; empty otherwise
     */
    static List<ConfigProblem> cupSelectionProblems(ServerSettings settings) {
        return cupSelectionProblems(settings, PROPERTY_SOURCE);
    }

    static List<ConfigProblem> cupSelectionProblems(ServerSettings settings, Function<String, String> sources) {
        Optional<String> refusal;
        try {
            refusal = CupResolution.refusalOf(CatalogLoader.read(settings.dataPath()), settings.cupName());
        } catch (RuntimeException exception) {
            return List.of();
        }
        if (refusal.isEmpty()) {
            return List.of();
        }
        String source = settings.cupName().isPresent()
                ? sources.apply(ServerSettings.CUP_PROPERTY)
                : settings.dataPath().resolve("cups").toAbsolutePath().toString();
        return List.of(error(ServerSettings.CUP_PROPERTY, source, refusal.get()));
    }

    /**
     * Every problem in the catalogue, the cup selection and the worlds of every map, sorted. This is the
     * whole-catalogue check the validate-and-exit run uses.
     *
     * <p>The worlds are opened through {@code instances}, which must belong to an initialised Minestom
     * server; nothing here starts that server or binds a socket. Each world is read and closed before the
     * next one is opened, so the check holds one world at a time.
     *
     * @param settings  settings whose directories exist
     * @param instances the instance manager of the initialised Minestom server
     * @return every problem; empty when the catalogue and every world it names are sound
     */
    public static List<ConfigProblem> catalogueProblems(ServerSettings settings, InstanceManager instances) {
        return catalogueProblems(settings, instances, PROPERTY_SOURCE);
    }

    /** {@link #catalogueProblems(ServerSettings, InstanceManager)} with each setting labelled by {@code sources}. */
    public static List<ConfigProblem> catalogueProblems(ServerSettings settings, InstanceManager instances,
            Function<String, String> sources) {
        return problems(settings, instances, map -> true, sources);
    }

    /**
     * Every problem a boot with these settings would see: the catalogue, the cup selection, and the worlds of
     * the maps of the played cup only. Without a played cup no world is opened, because the cup selection error
     * already refuses the boot.
     */
    private static List<ConfigProblem> playedCupProblems(ServerSettings settings, InstanceManager instances,
            Optional<CupDefinition> played, Function<String, String> sources) {
        Predicate<MapDefinition> playedMap = played
                .<Predicate<MapDefinition>>map(cup -> map -> cup.mapNames().contains(map.name()))
                .orElse(map -> false);
        return problems(settings, instances, playedMap, sources);
    }

    private static List<ConfigProblem> problems(ServerSettings settings, InstanceManager instances,
            Predicate<MapDefinition> checkWorld, Function<String, String> sources) {
        Objects.requireNonNull(instances, "the instance manager must come from an initialised server");
        List<ConfigProblem> problems = new ArrayList<>(cupSelectionProblems(settings, sources));
        MapInstances worlds = new MapInstances(instances, settings.worldsPath());
        try {
            problems.addAll(CatalogValidation.validate(settings.dataPath(), settings.worldsPath(), checkWorld,
                    worlds::checkWorld));
            problems.addAll(lobbyProblems(settings, worlds, sources));
        } finally {
            worlds.close();
        }
        problems.sort(ConfigProblem.ORDER);
        return List.copyOf(problems);
    }

    /**
     * The problems a normal boot refuses with: every problem of the catalogue and the cup selection, the worlds
     * of the played cup's maps only, and none of a cup that is not played. The worlds of the other cups are not
     * opened at all. The unplayed cups' problems are not dropped; the composition root logs them as one
     * warning, from {@link CupResolution#skippedCups}.
     *
     * <p>When no cup can be picked, nothing is set aside: every error refuses, and the ambiguity is one of
     * them. A malformed unplayed cup file therefore still refuses a directory with two cup files and no name,
     * which is the owner's decision for that case.
     *
     * @param settings  settings whose directories exist
     * @param instances the instance manager of the initialised Minestom server
     * @return the errors that refuse boot, sorted; empty when boot may proceed
     */
    public static List<ConfigProblem> bootRefusals(ServerSettings settings, InstanceManager instances) {
        return bootRefusals(settings, instances, PROPERTY_SOURCE);
    }

    /** {@link #bootRefusals(ServerSettings, InstanceManager)} with each setting labelled by {@code sources}. */
    public static List<ConfigProblem> bootRefusals(ServerSettings settings, InstanceManager instances,
            Function<String, String> sources) {
        Optional<CupDefinition> played = playedCup(settings);
        List<ConfigProblem> errors = playedCupProblems(settings, instances, played, sources).stream()
                .filter(problem -> problem.severity() == Severity.ERROR)
                .toList();
        if (played.isEmpty()) {
            return errors;
        }
        CatalogReading reading = CatalogLoader.read(settings.dataPath());
        Path playedFile = reading.cupFilesByName().get(played.get().name()).toAbsolutePath().normalize();
        Path cupsDirectory = settings.dataPath().resolve("cups").toAbsolutePath().normalize();
        return errors.stream()
                .filter(problem -> !isOtherCupFile(problem.source(), cupsDirectory, playedFile))
                .toList();
    }

    private static Optional<CupDefinition> playedCup(ServerSettings settings) {
        try {
            return Optional.of(CupResolution.resolve(CatalogLoader.read(settings.dataPath()), settings.cupName()));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    /** Whether {@code source} is a cup file other than the played one: a problem a boot only warns about. */
    private static boolean isOtherCupFile(String source, Path cupsDirectory, Path playedFile) {
        Path file = Path.of(source).toAbsolutePath().normalize();
        return cupsDirectory.equals(file.getParent()) && !file.equals(playedFile);
    }

    /**
     * The check as one call: the catalogue problems, printed, with the exit code.
     *
     * @param settings  settings whose directories exist
     * @param instances the instance manager of the initialised Minestom server
     * @param out       the stream the report is printed to
     * @return 0 when there is no error, 1 when there is at least one
     */
    public static int run(ServerSettings settings, InstanceManager instances, PrintStream out) {
        return run(settings, instances, out, PROPERTY_SOURCE);
    }

    /** {@link #run(ServerSettings, InstanceManager, PrintStream)} with each setting labelled by {@code sources}. */
    public static int run(ServerSettings settings, InstanceManager instances, PrintStream out,
            Function<String, String> sources) {
        return report(catalogueProblems(settings, instances, sources), out);
    }

    /**
     * Prints every problem, one per line, then a summary line.
     *
     * @param problems the problems to print, already sorted
     * @param out      the stream the report is printed to
     * @return 0 when no problem is an error, 1 when at least one is
     */
    public static int report(List<ConfigProblem> problems, PrintStream out) {
        long errors = 0;
        for (ConfigProblem problem : problems) {
            out.println(problem.format());
            if (problem.severity() == Severity.ERROR) {
                errors++;
            }
        }
        if (errors == 0) {
            out.println("config check passed with %d warning(s)".formatted(problems.size()));
            return 0;
        }
        out.println("config check failed: %d error(s)".formatted(errors));
        return 1;
    }

    /**
     * The lobby world, checked the way a map's world is, and the one rule that is about the lobby as a whole: no lobby is a
     * warning, and a lobby may not be a catalogue map's world. The world is read and closed before this returns, so the
     * check holds one world open at a time, as {@link MapInstances#checkWorld} does for each map's world.
     */
    private static List<ConfigProblem> lobbyProblems(ServerSettings settings, MapInstances worlds,
            Function<String, String> sources) {
        Optional<LobbyDefinition> lobby = settings.lobby();
        if (lobby.isEmpty()) {
            return List.of(new ConfigProblem(ServerSettings.LOBBY_WORLD_PROPERTY,
                    sources.apply(ServerSettings.LOBBY_WORLD_PROPERTY),
                    "no lobby world is set, so waiting racers stand at the first map's spawn; set %s and %s to give them a lobby (ADR-0029)"
                            .formatted(ServerSettings.LOBBY_WORLD_PROPERTY, ServerSettings.LOBBY_SPAWN_PROPERTY),
                    Severity.WARNING));
        }
        String world = lobby.get().world();
        Optional<MapDefinition> played = CatalogLoader.read(settings.dataPath()).snapshot().maps().values().stream()
                .filter(map -> map.world().equals(world))
                .findFirst();
        if (played.isPresent()) {
            return List.of(error(ServerSettings.LOBBY_WORLD_PROPERTY, sources.apply(ServerSettings.LOBBY_WORLD_PROPERTY),
                    "%s names world '%s', which map '%s' plays; a racer waiting in a map's world is inside that map's race"
                            .formatted(ServerSettings.LOBBY_WORLD_PROPERTY, world, played.get().name())));
        }
        String folder = settings.worldsPath().resolve(world).toAbsolutePath().toString();
        return switch (WorldFolders.check(settings.worldsPath(), world)) {
            case MISSING -> List.of(error(ServerSettings.LOBBY_WORLD_PROPERTY, folder,
                    "the lobby world '%s' is not a folder; looked for %s".formatted(world, folder)));
            case NO_REGION_DATA -> List.of(error(ServerSettings.LOBBY_WORLD_PROPERTY, folder,
                    "the lobby world '%s' holds no region data; looked for region files in %s and in %s"
                            .formatted(world, folder + "/region", folder + "/dimensions")));
            case PRESENT -> lobbyHealthProblems(world, folder, worlds);
        };
    }

    private static List<ConfigProblem> lobbyHealthProblems(String world, String folder, MapInstances worlds) {
        try {
            WorldHealth health = worlds.checkWorld(world);
            return health.isSound()
                    ? List.of()
                    : List.of(error(ServerSettings.LOBBY_WORLD_PROPERTY, folder, health.describe()));
        } catch (RuntimeException exception) {
            String reason = Optional.ofNullable(exception.getMessage()).orElse(exception.getClass().getName());
            return List.of(error(ServerSettings.LOBBY_WORLD_PROPERTY, folder,
                    "the lobby world '%s' could not be read: %s".formatted(world, reason)));
        }
    }

    /** A lobby setting that cannot be used, keyed under the setting that is at fault. */
    private static void lobbySettingProblems(List<ConfigProblem> problems, Map<String, String> properties,
            Function<String, String> sources) {
        try {
            ServerSettings.parseLobby(blankToNull(properties.get(ServerSettings.LOBBY_WORLD_PROPERTY)),
                    blankToNull(properties.get(ServerSettings.LOBBY_SPAWN_PROPERTY)));
        } catch (InvalidSettingException exception) {
            problems.add(error(exception.setting(), sources.apply(exception.setting()), exception.getMessage()));
        }
        int maximum;
        try {
            maximum = ServerSettings.parseMaximumRacers(blankToNull(properties.get(ServerSettings.MAX_PLAYERS_PROPERTY)));
        } catch (InvalidSettingException exception) {
            problems.add(error(ServerSettings.MAX_PLAYERS_PROPERTY,
                    sources.apply(ServerSettings.MAX_PLAYERS_PROPERTY), exception.getMessage()));
            return;
        }
        try {
            int minimum = ServerSettings.minimumRacersFrom(
                    blankToNull(properties.get(ServerSettings.MIN_RACERS_PROPERTY)),
                    Boolean.parseBoolean(properties.get(ServerSettings.DEV_MODE_PROPERTY)));
            ServerSettings.requireMaximumRacers(maximum, minimum);
        } catch (InvalidSettingException exception) {
            problems.add(error(ServerSettings.MAX_PLAYERS_PROPERTY,
                    sources.apply(ServerSettings.MAX_PLAYERS_PROPERTY), exception.getMessage()));
        } catch (IllegalArgumentException ignored) {
            // A minimum that is not a number is reported by minimumRacersProblem, once.
        }
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static void directoryProblem(List<ConfigProblem> problems, Map<String, String> properties,
            String property, String fallback, String purpose) {
        Path path = Path.of(Objects.requireNonNullElse(properties.get(property), fallback));
        if (!ServerSettings.isDirectory(path)) {
            problems.add(error(property, path.toAbsolutePath().toString(),
                    new MissingServerDirectoryException(purpose, path).getMessage()));
        }
    }

    /**
     * The minimum racer setting, when it is set: a value that is not a whole number or is below one is refused under
     * its key. An unset or blank value is no problem, because the run's mode supplies the default.
     */
    private static void minimumRacersProblem(List<ConfigProblem> problems, Map<String, String> properties,
            Function<String, String> sources) {
        String raw = properties.get(ServerSettings.MIN_RACERS_PROPERTY);
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            ServerSettings.requireMinimumRacers(ServerSettings.parseMinimumRacers(raw));
        } catch (IllegalArgumentException exception) {
            problems.add(error(ServerSettings.MIN_RACERS_PROPERTY,
                    sources.apply(ServerSettings.MIN_RACERS_PROPERTY), exception.getMessage()));
        }
    }

    /**
     * The bind port a node sets through the system property, checked only when no positional port overrides it, as the
     * settings read it. The message names the property.
     */
    private static void bindPortProblem(List<ConfigProblem> problems, Map<String, String> properties,
            Function<String, String> sources) {
        try {
            ServerSettings.bindPort(properties.get(ServerSettings.BIND_PORT_PROPERTY));
        } catch (IllegalArgumentException exception) {
            problems.add(error(ServerSettings.BIND_PORT_PROPERTY,
                    sources.apply(ServerSettings.BIND_PORT_PROPERTY), exception.getMessage()));
        }
    }

    private static ConfigProblem error(String key, String source, String message) {
        return new ConfigProblem(key, source, message, Severity.ERROR);
    }
}
