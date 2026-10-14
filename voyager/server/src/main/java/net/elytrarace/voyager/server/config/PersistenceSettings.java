package net.elytrarace.voyager.server.config;

import net.elytrarace.voyager.api.config.ConfigProblem;
import net.elytrarace.voyager.api.config.DatabaseConfig;
import net.elytrarace.voyager.platform.config.ConfigLoader;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * The persistence settings of the server, read once at boot: the switch {@code voyager.persistence} and the database keys
 * under {@code voyager.db}. A problem with them never refuses the start (owner decision Q1): it becomes one warning, and the
 * server runs with statistics off.
 *
 * <p>Two rules this class keeps. The password file is read here, once, and is not read at all when the switch is off. And
 * no value of the password reaches a warning: a warning names the setting, and {@link DatabaseConfig} masks its secret.
 *
 * @param database the configuration the server runs with; statistics are on only when it is enabled
 * @param warnings the one problem to log, or none; for the switch unset or off it is the notice that no statistics are kept
 * @param requested whether the switch asks for statistics, that is, is set to something other than {@code off}
 */
public record PersistenceSettings(DatabaseConfig database, List<ConfigProblem> warnings, boolean requested) {

    /** The switch: {@code off} or {@code on}; unset means off. */
    public static final String SWITCH_KEY = "voyager.persistence";
    /** The JDBC URL, required when the switch is on. */
    public static final String URL_KEY = "voyager.db.url";
    /** The database user, required when the switch is on. */
    public static final String USER_KEY = "voyager.db.user";
    /** The password, required when the switch is on and no password file is named. */
    public static final String PASSWORD_KEY = "voyager.db.password";
    /** A file whose content is the password; it wins over {@link #PASSWORD_KEY} when both are set. */
    public static final String PASSWORD_FILE_KEY = "voyager.db.password.file";
    /** The connection pool size, from 2 to 20. */
    public static final String POOL_SIZE_KEY = "voyager.db.pool.size";
    /** The completions buffered during an outage, from 16 to 4096. */
    public static final String QUEUE_CAPACITY_KEY = "voyager.db.queue.capacity";

    /** Every key these settings read, so the loader can hand over one view of them. */
    public static final List<String> KEYS = List.of(SWITCH_KEY, URL_KEY, USER_KEY, PASSWORD_KEY, PASSWORD_FILE_KEY,
            POOL_SIZE_KEY, QUEUE_CAPACITY_KEY);

    /** The loader key of each environment name {@link DatabaseConfig} reports its problems under. */
    private static final Map<String, String> KEY_OF_SETTING = Map.of(
            DatabaseConfig.KEY_PERSISTENCE, SWITCH_KEY,
            DatabaseConfig.KEY_URL, URL_KEY,
            DatabaseConfig.KEY_USER, USER_KEY,
            DatabaseConfig.KEY_PASSWORD, PASSWORD_KEY,
            DatabaseConfig.KEY_POOL_SIZE, POOL_SIZE_KEY,
            DatabaseConfig.KEY_QUEUE_CAPACITY, QUEUE_CAPACITY_KEY);

    private static final String OFF_MESSAGE = "no statistics are kept on this server (VOYAGER_PERSISTENCE is unset or off); "
            + "the game runs normally";

    private static final String PASSWORD_FILE_SETTING = "VOYAGER_DB_PASSWORD_FILE";

    public PersistenceSettings {
        Objects.requireNonNull(database, "database must not be null");
        warnings = List.copyOf(warnings);
    }

    /**
     * The settings of the running JVM, read through the loader once.
     *
     * @param loader the loader of the server
     * @return the settings
     */
    public static PersistenceSettings from(ConfigLoader loader) {
        return from(loader.view(KEYS), ConfigCheck.sourcesOf(loader));
    }

    /**
     * The settings of a set of values, by the loader's keys. Tests use it; the server calls it through {@link #from(ConfigLoader)}.
     *
     * @param values  the values by key; an absent or blank value is unset
     * @param sources the label of the source of a key, for the problem a warning reports
     * @return the settings
     */
    public static PersistenceSettings from(Map<String, String> values, Function<String, String> sources) {
        String mode = text(values, SWITCH_KEY);
        if (mode == null || "off".equals(mode)) {
            return new PersistenceSettings(DatabaseConfig.from(Map.of()), List.of(offNotice(sources)), false);
        }
        if (!"on".equals(mode)) {
            return withConfig(DatabaseConfig.from(Map.of(DatabaseConfig.KEY_PERSISTENCE, mode)), sources);
        }
        Map<String, String> settings = new HashMap<>();
        settings.put(DatabaseConfig.KEY_PERSISTENCE, mode);
        settings.put(DatabaseConfig.KEY_URL, text(values, URL_KEY));
        settings.put(DatabaseConfig.KEY_USER, text(values, USER_KEY));
        settings.put(DatabaseConfig.KEY_POOL_SIZE, text(values, POOL_SIZE_KEY));
        settings.put(DatabaseConfig.KEY_QUEUE_CAPACITY, text(values, QUEUE_CAPACITY_KEY));
        String passwordFile = text(values, PASSWORD_FILE_KEY);
        if (passwordFile != null) {
            PasswordFile read = readPassword(passwordFile);
            if (read.secret() == null) {
                return new PersistenceSettings(DatabaseConfig.from(Map.of()), List.of(problem(PASSWORD_FILE_KEY,
                        read.reason(), sources)), true);
            }
            settings.put(DatabaseConfig.KEY_PASSWORD, read.secret());
        } else {
            settings.put(DatabaseConfig.KEY_PASSWORD, text(values, PASSWORD_KEY));
        }
        return withConfig(DatabaseConfig.from(settings), sources);
    }

    /**
     * The settings with the switch unset: statistics off, one notice. What {@code openGraph} uses when no settings are given.
     *
     * @return the off settings
     */
    public static PersistenceSettings off() {
        return from(Map.of(), key -> key);
    }

    private static PersistenceSettings withConfig(DatabaseConfig database, Function<String, String> sources) {
        List<ConfigProblem> warnings = new ArrayList<>();
        if (database.warning() != null) {
            String setting = database.warning().substring(0, database.warning().indexOf(' '));
            String key = KEY_OF_SETTING.getOrDefault(setting, SWITCH_KEY);
            warnings.add(problem(key, database.warning(), sources));
        }
        return new PersistenceSettings(database, warnings, true);
    }

    private static ConfigProblem offNotice(Function<String, String> sources) {
        return problem(SWITCH_KEY, OFF_MESSAGE, sources);
    }

    private static ConfigProblem problem(String key, String message, Function<String, String> sources) {
        return new ConfigProblem(key, sources.apply(key), message, ConfigProblem.Severity.WARNING);
    }

    /** The content of the password file, or the reason it could not be used. The reason never contains the content. */
    private static PasswordFile readPassword(String file) {
        try {
            String content = Files.readString(Path.of(file), StandardCharsets.UTF_8);
            String secret = content.endsWith("\n") ? content.substring(0, content.length() - 1) : content;
            secret = secret.endsWith("\r") ? secret.substring(0, secret.length() - 1) : secret;
            if (secret.isBlank()) {
                return new PasswordFile(null, "%s is empty; statistics stay off".formatted(PASSWORD_FILE_SETTING));
            }
            return new PasswordFile(secret, null);
        } catch (IOException | InvalidPathException failure) {
            return new PasswordFile(null, "%s cannot be read (%s); statistics stay off"
                    .formatted(PASSWORD_FILE_SETTING, failure.getClass().getSimpleName()));
        }
    }

    @Nullable
    private static String text(Map<String, String> values, String key) {
        String value = values.get(key);
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** The outcome of reading the password file: the secret, or the reason there is none. */
    private record PasswordFile(@Nullable String secret, @Nullable String reason) {
    }
}
