package net.elytrarace.voyager.persistence.schema;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

import javax.sql.DataSource;

/**
 * Brings the statistics schema up to date with Flyway: the migrations of {@code db/migration} run in schema
 * {@code voyager}, under PostgreSQL's advisory lock, so two servers starting together apply each migration once.
 *
 * <p>Flyway runs on the same pool that Hibernate uses (ADR-0037, design D9), so the database is reached through one
 * set of connection settings. Flyway borrows connections from the pool and never closes it. It never baselines an
 * existing database ({@code baselineOnMigrate} is off) and never cleans: a schema that is not what the migrations
 * describe fails the start rather than being repaired behind the operator's back.
 */
public final class FlywayMigrator {

    /** The schema that holds every statistics table (design D4). */
    public static final String SCHEMA = "voyager";

    private FlywayMigrator() {
    }

    /**
     * Applies every pending migration.
     *
     * @param dataSource the pool shared with the session factory; it stays open after this call
     * @return the number of migrations this call applied; zero when the database is already at the latest version
     * @throws org.flywaydb.core.api.FlywayException when a migration fails or the schema history does not validate
     */
    public static int migrate(DataSource dataSource) {
        MigrateResult result = Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .createSchemas(true)
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .load()
                .migrate();
        return result.migrationsExecuted;
    }
}
