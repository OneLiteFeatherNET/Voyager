package net.elytrarace.voyager.persistence.schema;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

/**
 * Creates the connection pool of the statistics store (design D9). Flyway and the session factory use the same pool
 * settings; the pool is created once at boot and closed after the writer on shutdown.
 */
@ApiStatus.Internal
public abstract class HikariDataSources {

    private static final long CONNECTION_TIMEOUT_MILLIS = 5_000L;
    private static final long MAX_LIFETIME_MILLIS = 1_500_000L;
    private static final long KEEPALIVE_TIME_MILLIS = 120_000L;
    private static final long LEAK_DETECTION_MILLIS = 10_000L;
    private static final String POOL_NAME = "voyager-statistics";

    private HikariDataSources() {
    }

    /**
     * Creates a started pool. The pool keeps its minimum idle connections equal to its maximum, so no request waits
     * for a connection to open.
     *
     * @param jdbcUrl         a {@code jdbc:postgresql://} URL
     * @param user            the database user
     * @param password        the password; HikariCP keeps it out of its own log lines
     * @param poolSize        the maximum number of connections, at least one
     * @param leakDetection   {@code true} reports a connection held for more than ten seconds (development);
     *                        {@code false} disables the detector (production)
     * @return the started pool; the caller closes it
     * @throws IllegalArgumentException when {@code poolSize} is below one
     * @throws RuntimeException when the database cannot be reached at creation (HikariCP's initialization failure)
     */
    @Contract("_, _, _, _, _ -> new")
    public static HikariDataSource create(
            String jdbcUrl, String user, String password, int poolSize, boolean leakDetection) {
        if (poolSize < 1) {
            throw new IllegalArgumentException("pool size must be at least one, was %s".formatted(poolSize));
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName(POOL_NAME);
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(poolSize);
        config.setConnectionTimeout(CONNECTION_TIMEOUT_MILLIS);
        config.setMaxLifetime(MAX_LIFETIME_MILLIS);
        config.setKeepaliveTime(KEEPALIVE_TIME_MILLIS);
        config.setLeakDetectionThreshold(leakDetection ? LEAK_DETECTION_MILLIS : 0L);
        config.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
        // pgjdbc rewrites a batch of inserts into one multi-row statement when this is set.
        config.addDataSourceProperty("reWriteBatchedInserts", "true");
        return new HikariDataSource(config);
    }
}
