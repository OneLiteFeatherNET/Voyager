package net.elytrarace.api.database.service;

import net.elytrarace.api.database.repository.ElytraPlayerRepository;
import net.elytrarace.api.database.repository.GameResultRepository;
import net.elytrarace.common.utils.ThreadHelper;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Optional;

final class DatabaseServiceImpl implements DatabaseService, ThreadHelper {

    private final @Nullable Path rootPath;
    private final @Nullable DatabaseConfig config;

    private volatile SessionFactory sessionFactory;
    private volatile ElytraPlayerRepository elytraPlayerRepository;
    private volatile GameResultRepository gameResultRepository;
    private volatile boolean initialized;

    DatabaseServiceImpl(@NotNull Path rootPath) {
        this.rootPath = rootPath;
        this.config = null;
    }

    DatabaseServiceImpl(@NotNull DatabaseConfig config) {
        this.config = config;
        this.rootPath = null;
    }

    @Override
    public void init() {
        try {
            this.sessionFactory = syncThreadForServiceLoader(this::createSessionFactory);
            this.elytraPlayerRepository = ElytraPlayerRepository.createInstance(sessionFactory);
            this.gameResultRepository = GameResultRepository.createInstance(sessionFactory);
            // Fail-fast connection probe: grab a session immediately so a bad pool or
            // unreachable DB surfaces during init() rather than on the first async call.
            sessionFactory.inSession(session -> session.doWork(connection -> {
                if (!connection.isValid(5)) {
                    throw new IllegalStateException("JDBC connection failed isValid() probe");
                }
            }));
            this.initialized = true;
        } catch (RuntimeException ex) {
            // Best-effort cleanup: a partially-built SessionFactory still holds connections.
            closeQuietly();
            throw new DatabaseInitializationException(
                    "Failed to initialize database service: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void close() {
        closeQuietly();
        this.initialized = false;
        this.elytraPlayerRepository = null;
        this.gameResultRepository = null;
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    private void closeQuietly() {
        SessionFactory sf = this.sessionFactory;
        if (sf != null && !sf.isClosed()) {
            try {
                sf.close();
            } catch (RuntimeException ignored) {
                // Swallow — we are already on an error / shutdown path.
            }
        }
        this.sessionFactory = null;
    }

    private SessionFactory createSessionFactory() {
        Configuration configuration = new Configuration().configure();
        if (config != null) {
            applyProgrammaticConfig(configuration, config);
        } else if (rootPath != null) {
            configuration.configure(rootPath.resolve(HIBERNATE_CONFIG_FILE_NAME).toFile());
        }
        return configuration.buildSessionFactory();
    }

    private static void applyProgrammaticConfig(Configuration configuration, DatabaseConfig config) {
        // MariaDB dialect + Hibernate + HikariCP connection provider.
        configuration.setProperty(AvailableSettings.CONNECTION_PROVIDER,
                "org.hibernate.hikaricp.internal.HikariCPConnectionProvider");
        configuration.setProperty(AvailableSettings.JAKARTA_JDBC_DRIVER, "org.mariadb.jdbc.Driver");
        configuration.setProperty(AvailableSettings.JAKARTA_JDBC_URL, config.jdbcUrl());
        configuration.setProperty(AvailableSettings.JAKARTA_JDBC_USER, config.username());
        configuration.setProperty(AvailableSettings.JAKARTA_JDBC_PASSWORD, config.password());

        configuration.setProperty(AvailableSettings.HBM2DDL_AUTO, config.hbm2ddlAction());
        configuration.setProperty(AvailableSettings.SHOW_SQL, String.valueOf(config.showSql()));
        configuration.setProperty(AvailableSettings.FORMAT_SQL, String.valueOf(config.showSql()));

        // HikariCP tuning — keys map 1:1 to com.zaxxer.hikari.HikariConfig setters.
        configuration.setProperty("hibernate.hikari.maximumPoolSize", String.valueOf(config.poolSize()));
        configuration.setProperty("hibernate.hikari.minimumIdle", String.valueOf(Math.min(2, config.poolSize())));
        configuration.setProperty("hibernate.hikari.idleTimeout", "30000");
        configuration.setProperty("hibernate.hikari.connectionTimeout", "10000");
        configuration.setProperty("hibernate.hikari.poolName", "VoyagerHikariPool");
    }

    @Override
    public Optional<ElytraPlayerRepository> getElytraPlayerRepository() {
        return Optional.ofNullable(this.elytraPlayerRepository);
    }

    @Override
    public Optional<GameResultRepository> getGameResultRepository() {
        return Optional.ofNullable(this.gameResultRepository);
    }
}
