package net.elytrarace.voyager.server.config;

import net.elytrarace.voyager.api.config.ConfigProblem;
import net.elytrarace.voyager.api.config.ConfigProblem.Severity;
import net.elytrarace.voyager.platform.catalog.CatalogLoader;
import net.elytrarace.voyager.platform.catalog.CatalogSnapshot;
import net.elytrarace.voyager.platform.catalog.CatalogValidation;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.server.config.exception.MissingServerDirectoryException;

import net.minestom.server.instance.InstanceManager;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
    private static final String CUP_SOURCE = "system property " + ServerSettings.CUP_PROPERTY;

    private ConfigCheck() {
    }

    /**
     * The JVM's system properties as a plain map, the form the settings read from.
     *
     * @return every system property that has a string value
     */
    public static Map<String, String> systemProperties() {
        Map<String, String> properties = new HashMap<>();
        for (String name : System.getProperties().stringPropertyNames()) {
            properties.put(name, System.getProperty(name));
        }
        return properties;
    }

    /**
     * The settings that cannot be used, each as one problem, without stopping at the first.
     *
     * @param args       the command line, {@code [host] [port]}
     * @param properties the system properties the settings read, by name
     * @return every problem with the settings; empty when {@link ServerSettings#fromProperties} would accept them
     */
    public static List<ConfigProblem> settingsProblems(String[] args, Map<String, String> properties) {
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
        directoryProblem(problems, properties, ServerSettings.DATA_PATH_PROPERTY,
                ServerSettings.DEFAULT_DATA_PATH, MissingServerDirectoryException.DATA);
        directoryProblem(problems, properties, ServerSettings.WORLDS_PATH_PROPERTY,
                ServerSettings.DEFAULT_WORLDS_PATH, MissingServerDirectoryException.WORLDS);
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
     * The one cup a named selection points at, when it does not exist.
     *
     * @param settings resolved settings whose data directory is present
     * @return one problem when {@code VOYAGER_CUP} names a cup the catalogue does not hold; empty otherwise
     */
    static List<ConfigProblem> cupSelectionProblems(ServerSettings settings) {
        Optional<String> wanted = settings.cupName();
        if (wanted.isEmpty()) {
            return List.of();
        }
        CatalogSnapshot snapshot = CatalogLoader.read(settings.dataPath()).snapshot();
        if (snapshot.cupByName(wanted.get()).isPresent()) {
            return List.of();
        }
        String available = snapshot.cupNames().isEmpty() ? "none" : String.join(", ", snapshot.cupNames());
        return List.of(error(ServerSettings.CUP_PROPERTY, CUP_SOURCE,
                "no cup named '%s' in %s; the cups are %s"
                        .formatted(wanted.get(), settings.dataPath().resolve("cups").toAbsolutePath(), available)));
    }

    /**
     * Every problem in the catalogue, the cup selection and the worlds, sorted.
     *
     * <p>The worlds are opened through {@code instances}, which must belong to an initialised Minestom
     * server; nothing here starts that server or binds a socket. Every world opened for the check is
     * closed again before this returns.
     *
     * @param settings  settings whose directories exist
     * @param instances the instance manager of the initialised Minestom server
     * @return every problem; empty when the catalogue and every world it names are sound
     */
    public static List<ConfigProblem> catalogueProblems(ServerSettings settings, InstanceManager instances) {
        Objects.requireNonNull(instances, "the instance manager must come from an initialised server");
        List<ConfigProblem> problems = new ArrayList<>(cupSelectionProblems(settings));
        MapInstances worlds = new MapInstances(instances, settings.worldsPath());
        try {
            problems.addAll(CatalogValidation.validate(settings.dataPath(), settings.worldsPath(), world -> {
                worlds.readEveryChunk(world);
                return worlds.healthOf(world);
            }));
        } finally {
            worlds.close();
        }
        problems.sort(ConfigProblem.ORDER);
        return List.copyOf(problems);
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
        return report(catalogueProblems(settings, instances), out);
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
     * Whether any problem fails the check.
     *
     * @param problems the problems to test
     * @return true when at least one is an error
     */
    public static boolean hasErrors(List<ConfigProblem> problems) {
        return problems.stream().anyMatch(problem -> problem.severity() == Severity.ERROR);
    }

    private static void directoryProblem(List<ConfigProblem> problems, Map<String, String> properties,
            String property, String fallback, String purpose) {
        Path path = Path.of(Objects.requireNonNullElse(properties.get(property), fallback));
        if (!ServerSettings.isDirectory(path)) {
            problems.add(error(property, path.toAbsolutePath().toString(),
                    new MissingServerDirectoryException(purpose, path).getMessage()));
        }
    }

    private static ConfigProblem error(String key, String source, String message) {
        return new ConfigProblem(key, source, message, Severity.ERROR);
    }
}
