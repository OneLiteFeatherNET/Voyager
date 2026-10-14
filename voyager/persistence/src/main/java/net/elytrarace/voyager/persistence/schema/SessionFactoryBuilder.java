package net.elytrarace.voyager.persistence.schema;

import net.elytrarace.voyager.persistence.entity.CupResultEntity;
import net.elytrarace.voyager.persistence.entity.CupSessionEntity;
import net.elytrarace.voyager.persistence.entity.MapPersonalBestEntity;
import net.elytrarace.voyager.persistence.entity.PlayerEntity;
import net.elytrarace.voyager.persistence.entity.PlayerStatisticsEntity;
import net.elytrarace.voyager.persistence.entity.RaceResultEntity;
import net.elytrarace.voyager.persistence.entity.RaceSessionEntity;
import net.elytrarace.voyager.persistence.entity.RaceSplitEntity;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.JdbcSettings;
import org.hibernate.cfg.SchemaToolingSettings;
import org.hibernate.jpa.HibernatePersistenceConfiguration;

import javax.sql.DataSource;
import java.time.ZoneId;

/**
 * Builds the Hibernate session factory of the statistics store over an existing pool.
 *
 * <p>Hibernate validates the mappings against the schema that Flyway has already created and never changes it
 * ({@code hbm2ddl.auto=validate}). The JDBC time zone is UTC, so the {@code timestamptz} columns round-trip the
 * instants the writer stores (design D9).
 */
public final class SessionFactoryBuilder {

    private static final String PERSISTENCE_UNIT = "voyager-statistics";

    private SessionFactoryBuilder() {
    }

    /**
     * Builds the factory. Building it validates every mapping; a mismatch with the schema fails here, not at the first
     * write.
     *
     * @param dataSource the pool that the factory takes its connections from; the caller keeps ownership of it
     * @return the session factory; the caller closes it before the pool
     * @throws org.hibernate.HibernateException when a mapping does not match the schema
     */
    public static SessionFactory build(DataSource dataSource) {
        return new HibernatePersistenceConfiguration(PERSISTENCE_UNIT)
                .managedClasses(
                        PlayerEntity.class,
                        PlayerStatisticsEntity.class,
                        RaceSessionEntity.class,
                        RaceResultEntity.class,
                        RaceSplitEntity.class,
                        CupSessionEntity.class,
                        CupResultEntity.class,
                        MapPersonalBestEntity.class)
                .defaultSchema(FlywayMigrator.SCHEMA)
                .property(AvailableSettings.JAKARTA_NON_JTA_DATASOURCE, dataSource)
                .property(SchemaToolingSettings.HBM2DDL_AUTO, "validate")
                .property(JdbcSettings.JDBC_TIME_ZONE, ZoneId.of("UTC"))
                .createEntityManagerFactory();
    }
}
