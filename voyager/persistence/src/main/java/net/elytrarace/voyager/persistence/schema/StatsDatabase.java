package net.elytrarace.voyager.persistence.schema;

import com.zaxxer.hikari.HikariDataSource;
import org.hibernate.SessionFactory;
import org.jetbrains.annotations.ApiStatus;

/**
 * The open statistics database: one Hikari pool, the schema brought up to date on it, and the session factory built
 * over the same pool (ADR-0037, design D9). The order is fixed: the pool is created first, Flyway migrates through it,
 * and only then does Hibernate validate the mappings against the migrated schema.
 *
 * @param dataSource     the pool; closed after the session factory
 * @param sessionFactory the factory the writer and the readers take their sessions from
 */
@ApiStatus.Internal
public record StatsDatabase(HikariDataSource dataSource, SessionFactory sessionFactory) implements AutoCloseable {

    /**
     * Opens the database. When any step fails, the pool is closed before the failure leaves this method, so a failed
     * start leaves no connection open.
     *
     * @param jdbcUrl       a {@code jdbc:postgresql://} URL
     * @param user          the database user
     * @param password      the password; it is passed on and never logged here
     * @param poolSize      the maximum number of connections
     * @param leakDetection {@code true} in development, {@code false} in production
     * @return the open database; the caller closes it
     * @throws RuntimeException when the pool cannot be created, a migration fails or a mapping does not match the
     *                          schema
     */
    public static StatsDatabase open(
            String jdbcUrl, String user, String password, int poolSize, boolean leakDetection) {
        HikariDataSource pool = HikariDataSources.create(jdbcUrl, user, password, poolSize, leakDetection);
        try {
            FlywayMigrator.migrate(pool);
            return new StatsDatabase(pool, SessionFactoryBuilder.build(pool));
        } catch (RuntimeException failure) {
            pool.close();
            throw failure;
        }
    }

    @Override
    public void close() {
        sessionFactory.close();
        dataSource.close();
    }
}
