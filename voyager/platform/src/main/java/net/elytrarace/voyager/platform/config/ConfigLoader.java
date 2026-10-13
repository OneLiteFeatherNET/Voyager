package net.elytrarace.voyager.platform.config;

import io.avaje.config.Configuration;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The one place that reads the server's configuration: {@code application.yaml} in the working directory, the file named by
 * {@code CONFIG_FILE}, the environment and the system properties, in avaje-config's order (ADR-0036).
 *
 * <p>Precedence, lowest to highest: the defaults in code, {@code application.yaml} in the working directory, the file named
 * by {@code CONFIG_FILE}, the environment variable, the system property. A key's environment variable is its name in upper
 * case with the dots removed to underscores, so {@code VOYAGER_CUP} is both the key and its variable. The velocity secret is
 * the one exception: the environment wins over the property there, as {@code server/proxy-forwarding} requires (see
 * {@link net.elytrarace.voyager.platform.proxy.ProxyForwardingSettings#fromLoader}).
 *
 * <p>An instance, not avaje-config's static facade: the composition root builds one, and tests build their own with
 * {@link #of}, so no configuration is shared between two runs.
 */
public final class ConfigLoader {

    /** The source name avaje-config gives a value read from a system property. */
    private static final String SYSTEM_PROPERTY_SOURCE = "SystemProperty";

    /** The source name avaje-config gives a value read from an environment variable. */
    private static final String ENVIRONMENT_SOURCE = "ENV";

    /** The prefix avaje-config puts on the name of a file it loaded from disk. */
    private static final String FILE_SOURCE_PREFIX = "file:";

    private final Configuration configuration;
    private final Map<String, String> environment;

    private ConfigLoader(Configuration configuration, Map<String, String> environment) {
        this.configuration = configuration;
        this.environment = environment;
    }

    /**
     * The loader for the running JVM: the classpath resources, the working directory, {@code CONFIG_FILE}, the environment and
     * the system properties. Read once, at boot.
     *
     * @return the loader
     */
    public static ConfigLoader fromJvm() {
        Configuration configuration = Configuration.builder()
                .log(new SlfConfigurationLog())
                .includeResourceLoading()
                .build();
        return new ConfigLoader(configuration, System.getenv());
    }

    /**
     * A loader over a configuration the caller built, and an environment the caller supplies. Tests use it; the environment
     * is a map because a running JVM cannot change its own.
     *
     * @param configuration the configuration that answers {@link #get}
     * @param environment   the environment variables the velocity secret is read from first
     * @return the loader
     */
    public static ConfigLoader of(Configuration configuration, Map<String, String> environment) {
        return new ConfigLoader(Objects.requireNonNull(configuration), Map.copyOf(environment));
    }

    /**
     * The value of a key after every source has been applied.
     *
     * @param key the key, with its spelling, such as {@code VOYAGER_CUP} or {@code service.bind.port}
     * @return the value as it was given, not trimmed, or {@code null} when no source sets the key
     */
    public @Nullable String get(String key) {
        return configuration.getNullable(key);
    }

    /**
     * The keys that are set, with their values. A key no source sets is absent from the map, not empty.
     *
     * @param keys the keys to ask for
     * @return the set keys and their values
     */
    public Map<String, String> view(Collection<String> keys) {
        Map<String, String> view = new HashMap<>();
        for (String key : keys) {
            String value = get(key);
            if (value != null) {
                view.put(key, value);
            }
        }
        return Map.copyOf(view);
    }

    /**
     * The environment variables this loader was given. The velocity secret reads its variable from here.
     *
     * @return the environment, by variable name
     */
    public Map<String, String> environment() {
        return environment;
    }

    /**
     * Where the value of a key came from, in words an operator can act on. The configuration check prints this with each
     * problem.
     *
     * @param key the key
     * @return for example {@code system property VOYAGER_CUP}, {@code environment variable VOYAGER_CUP} or
     *     {@code configuration file application.yaml}; {@code absent} when no source sets the key
     */
    public String sourceOf(String key) {
        if (get(key) == null) {
            return "absent";
        }
        return configuration.entry(key)
                .map(entry -> describe(key, entry.source()))
                .orElse("absent");
    }

    /**
     * The variable name avaje-config reads for a key: dots become underscores, dashes are removed and the rest is upper case.
     * It is written here as well so that a message can name the variable; a test pins it against avaje-config's own mapping.
     *
     * @param key the key
     * @return the environment variable name
     */
    static String environmentName(String key) {
        return key.replace('.', '_').replace("-", "").toUpperCase(Locale.ROOT);
    }

    private static String describe(String key, String source) {
        if (SYSTEM_PROPERTY_SOURCE.equals(source)) {
            return "system property " + key;
        }
        if (ENVIRONMENT_SOURCE.equals(source)) {
            return "environment variable " + environmentName(key);
        }
        if (source.startsWith(FILE_SOURCE_PREFIX)) {
            return "configuration file " + source.substring(FILE_SOURCE_PREFIX.length());
        }
        return "configuration " + source;
    }
}
