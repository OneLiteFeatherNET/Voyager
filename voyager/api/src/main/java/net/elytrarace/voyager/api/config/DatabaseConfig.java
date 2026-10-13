package net.elytrarace.voyager.api.config;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The database settings of the server, read from the setting names of the operator's guide. Statistics are
 * on only when {@code VOYAGER_PERSISTENCE} is {@code on} and every other setting it needs is valid. Any
 * problem turns statistics off and is reported once in {@link #warning()}; it never refuses the start.
 *
 * <p>Carries no I/O: the password file is read by the caller, which puts the content under
 * {@link #KEY_PASSWORD} before calling {@link #from(Map)}.
 *
 * @param enabled       whether statistics are recorded
 * @param url           the JDBC URL, present exactly when {@code enabled}
 * @param user          the database user, present exactly when {@code enabled}
 * @param password      the password, present exactly when {@code enabled}
 * @param poolSize      the connection pool size, from 2 to 20
 * @param queueCapacity the completions buffered during an outage, from 16 to 4096
 * @param warning       the one warning to log, or {@code null} when there is nothing to report
 */
public record DatabaseConfig(boolean enabled, @Nullable String url, @Nullable String user,
        @Nullable Secret password, int poolSize, int queueCapacity, @Nullable String warning) {

    public static final String KEY_PERSISTENCE = "VOYAGER_PERSISTENCE";
    public static final String KEY_URL = "VOYAGER_DB_URL";
    public static final String KEY_USER = "VOYAGER_DB_USER";
    public static final String KEY_PASSWORD = "VOYAGER_DB_PASSWORD";
    public static final String KEY_POOL_SIZE = "VOYAGER_DB_POOL_SIZE";
    public static final String KEY_QUEUE_CAPACITY = "VOYAGER_DB_QUEUE_CAPACITY";

    static final int DEFAULT_POOL_SIZE = 10;
    static final int MIN_POOL_SIZE = 2;
    static final int MAX_POOL_SIZE = 20;
    static final int DEFAULT_QUEUE_CAPACITY = 256;
    static final int MIN_QUEUE_CAPACITY = 16;
    static final int MAX_QUEUE_CAPACITY = 4096;

    private static final String URL_PREFIX = "jdbc:postgresql://";

    public DatabaseConfig {
        if (poolSize < MIN_POOL_SIZE || poolSize > MAX_POOL_SIZE) {
            throw new IllegalArgumentException("pool size must be from %s to %s, was %s"
                    .formatted(MIN_POOL_SIZE, MAX_POOL_SIZE, poolSize));
        }
        if (queueCapacity < MIN_QUEUE_CAPACITY || queueCapacity > MAX_QUEUE_CAPACITY) {
            throw new IllegalArgumentException("queue capacity must be from %s to %s, was %s"
                    .formatted(MIN_QUEUE_CAPACITY, MAX_QUEUE_CAPACITY, queueCapacity));
        }
        if (enabled && (url == null || user == null || password == null)) {
            throw new IllegalArgumentException("an enabled database needs a URL, a user and a password");
        }
        if (enabled && warning != null) {
            throw new IllegalArgumentException("an enabled database carries no warning");
        }
    }

    /**
     * Reads the database settings. An unset or {@code off} switch gives a disabled configuration without a
     * warning; {@code on} with a problem gives a disabled configuration with one warning naming the setting.
     *
     * @param settings the raw values by setting name; a missing or blank value counts as unset
     * @return the configuration the server runs with
     */
    public static DatabaseConfig from(Map<String, String> settings) {
        String mode = text(settings, KEY_PERSISTENCE);
        if (mode == null || "off".equals(mode)) {
            return disabled(null);
        }
        if (!"on".equals(mode)) {
            return disabled(warn(KEY_PERSISTENCE, "must be off or on, was '%s'".formatted(mode)));
        }
        String url = text(settings, KEY_URL);
        if (url == null) {
            return disabled(warn(KEY_URL, "is not set"));
        }
        if (!url.startsWith(URL_PREFIX)) {
            return disabled(warn(KEY_URL, "must start with %s".formatted(URL_PREFIX)));
        }
        String user = text(settings, KEY_USER);
        if (user == null) {
            return disabled(warn(KEY_USER, "is not set"));
        }
        String password = text(settings, KEY_PASSWORD);
        if (password == null) {
            return disabled(warn(KEY_PASSWORD, "is not set"));
        }
        Integer poolSize = bounded(text(settings, KEY_POOL_SIZE), DEFAULT_POOL_SIZE, MIN_POOL_SIZE, MAX_POOL_SIZE);
        if (poolSize == null) {
            return disabled(warn(KEY_POOL_SIZE, "must be a whole number from %s to %s".formatted(
                    MIN_POOL_SIZE, MAX_POOL_SIZE)));
        }
        Integer queueCapacity = bounded(text(settings, KEY_QUEUE_CAPACITY), DEFAULT_QUEUE_CAPACITY,
                MIN_QUEUE_CAPACITY, MAX_QUEUE_CAPACITY);
        if (queueCapacity == null) {
            return disabled(warn(KEY_QUEUE_CAPACITY, "must be a whole number from %s to %s".formatted(
                    MIN_QUEUE_CAPACITY, MAX_QUEUE_CAPACITY)));
        }
        return new DatabaseConfig(true, url, user, new Secret(password), poolSize, queueCapacity, null);
    }

    private static DatabaseConfig disabled(@Nullable String warning) {
        return new DatabaseConfig(false, null, null, null, DEFAULT_POOL_SIZE, DEFAULT_QUEUE_CAPACITY, warning);
    }

    private static String warn(String key, String reason) {
        return "%s %s; statistics stay off".formatted(key, reason);
    }

    @Nullable
    private static String text(Map<String, String> settings, String key) {
        String value = settings.get(key);
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Parses a whole number in range; a missing value gives the default, a bad one gives {@code null}. */
    @Nullable
    private static Integer bounded(@Nullable String text, int fallback, int min, int max) {
        if (text == null) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(text);
            return value < min || value > max ? null : value;
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }
}
