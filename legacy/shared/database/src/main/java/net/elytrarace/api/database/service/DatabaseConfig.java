package net.elytrarace.api.database.service;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Connection settings used by {@link DatabaseService} to build the Hibernate
 * {@link org.hibernate.SessionFactory}.
 * <p>
 * All fields are required and non-null. Use {@link #fromEnvironment()} to load
 * values from environment variables and system properties with sensible
 * defaults for local development.
 *
 * @param jdbcUrl        full MariaDB JDBC URL, e.g. {@code jdbc:mariadb://localhost:3306/voyager-project}
 * @param username       database user
 * @param password       database password
 * @param poolSize       HikariCP maximum pool size (must be >= 1)
 * @param hbm2ddlAction  Hibernate schema action: {@code update}, {@code validate}, {@code create}, {@code create-drop}, {@code none}
 * @param showSql        whether Hibernate should log generated SQL
 */
public record DatabaseConfig(
        @NotNull String jdbcUrl,
        @NotNull String username,
        @NotNull String password,
        int poolSize,
        @NotNull String hbm2ddlAction,
        boolean showSql
) {

    /** Default JDBC URL pointing at the local Docker Compose MariaDB instance. */
    public static final String DEFAULT_JDBC_URL = "jdbc:mariadb://localhost:3306/voyager-project";
    /** Default user matching {@code docker/mariadb/compose.yml}. */
    public static final String DEFAULT_USERNAME = "voyager-project";
    /** Conservative pool default suitable for a single-node game server. */
    public static final int DEFAULT_POOL_SIZE = 10;
    /**
     * Default schema action for local development: {@code update}.
     * <p>
     * Production and staging deployments MUST override this to {@code validate}
     * via the {@code VOYAGER_DB_HBM2DDL} environment variable. Per ADR-0011,
     * Flyway is the sole DDL authority and Hibernate only verifies that the
     * live schema matches the entity model. Letting {@code update} run in
     * production risks silent, unaudited DDL on top of Flyway-managed tables.
     */
    public static final String DEFAULT_HBM2DDL = "update";

    public DatabaseConfig {
        Objects.requireNonNull(jdbcUrl, "jdbcUrl must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(password, "password must not be null");
        Objects.requireNonNull(hbm2ddlAction, "hbm2ddlAction must not be null");
        if (poolSize < 1) {
            throw new IllegalArgumentException("poolSize must be >= 1, was " + poolSize);
        }
    }

    /**
     * Reads configuration from environment variables first, then system properties,
     * falling back to local-dev defaults. Recognised keys:
     * <ul>
     *     <li>{@code VOYAGER_DB_URL}</li>
     *     <li>{@code VOYAGER_DB_USER}</li>
     *     <li>{@code VOYAGER_DB_PASSWORD}</li>
     *     <li>{@code VOYAGER_DB_POOL_SIZE}</li>
     *     <li>{@code VOYAGER_DB_HBM2DDL}</li>
     *     <li>{@code VOYAGER_DB_SHOW_SQL}</li>
     * </ul>
     *
     * @return a config instance ready to be passed to {@link DatabaseService#create(DatabaseConfig)}
     */
    public static DatabaseConfig fromEnvironment() {
        return new DatabaseConfig(
                lookup("VOYAGER_DB_URL", DEFAULT_JDBC_URL),
                lookup("VOYAGER_DB_USER", DEFAULT_USERNAME),
                lookup("VOYAGER_DB_PASSWORD", ""),
                parseInt(lookup("VOYAGER_DB_POOL_SIZE", String.valueOf(DEFAULT_POOL_SIZE)), DEFAULT_POOL_SIZE),
                lookup("VOYAGER_DB_HBM2DDL", DEFAULT_HBM2DDL),
                Boolean.parseBoolean(lookup("VOYAGER_DB_SHOW_SQL", "false"))
        );
    }

    private static String lookup(String key, String fallback) {
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            return env;
        }
        String prop = System.getProperty(key);
        if (prop != null && !prop.isBlank()) {
            return prop;
        }
        return fallback;
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
