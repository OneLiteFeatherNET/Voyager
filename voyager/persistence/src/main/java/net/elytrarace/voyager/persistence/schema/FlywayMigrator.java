package net.elytrarace.voyager.persistence.schema;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

/**
 * Brings the statistics schema up to date with Flyway: the migrations of {@code db/migration} run in schema
 * {@code voyager}, under PostgreSQL's advisory lock, so two servers starting together apply each migration once.
 *
 * <p>Flyway never baselines an existing database, never validates a database it did not create ({@code
 * baselineOnMigrate} is off), and never cleans: a schema that is not what the migrations describe fails the start
 * rather than being repaired behind the operator's back (design D9).
 */
public final class FlywayMigrator {

    /** The schema that holds every statistics table (design D4). */
    public static final String SCHEMA = "voyager";

    private FlywayMigrator() {
    }

    /**
     * Applies every pending migration.
     *
     * @param jdbcUrl  a {@code jdbc:postgresql://} URL of the database
     * @param user     the database user that owns or may create the schema
     * @param password the password of {@code user}; the caller reads it from the secret and it is never logged here
     * @return the number of migrations this call applied; zero when the database is already at the latest version
     * @throws org.flywaydb.core.api.FlywayException when a migration fails or the schema history does not validate
     */
    public static int migrate(String jdbcUrl, String user, String password) {
        MigrateResult result = Flyway.configure()
                .dataSource(jdbcUrl, user, password)
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
