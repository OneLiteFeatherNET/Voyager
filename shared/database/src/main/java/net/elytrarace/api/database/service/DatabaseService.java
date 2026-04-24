package net.elytrarace.api.database.service;

import net.elytrarace.api.database.repository.ElytraPlayerRepository;
import net.elytrarace.api.database.repository.GameResultRepository;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Owns the Hibernate {@link org.hibernate.SessionFactory} and exposes repositories
 * for the persistence layer. Implementations must be lifecycle-managed:
 * call {@link #init()} exactly once on startup and {@link #close()} on shutdown.
 */
public sealed interface DatabaseService permits DatabaseServiceImpl {

    String HIBERNATE_CONFIG_FILE_NAME = "hibernate.cfg.xml";

    /**
     * Initializes the database service. Builds the {@code SessionFactory},
     * eagerly validates the connection pool, and wires up repository instances.
     *
     * @throws DatabaseInitializationException if the database is unreachable or misconfigured
     */
    void init();

    /**
     * Closes the underlying {@code SessionFactory} and releases HikariCP resources.
     * Safe to call multiple times; subsequent calls are no-ops.
     */
    void close();

    /**
     * @return {@code true} once {@link #init()} has completed successfully
     */
    boolean isInitialized();

    /**
     * @return The ElytraPlayerRepository if it was initialized
     */
    Optional<ElytraPlayerRepository> getElytraPlayerRepository();

    /**
     * @return The GameResultRepository if it was initialized
     */
    Optional<GameResultRepository> getGameResultRepository();

    /**
     * Creates a database service configured via an external {@code hibernate.cfg.xml}
     * file at {@code rootPath}. Kept for the legacy Paper plugin; new callers should
     * prefer {@link #create(DatabaseConfig)}.
     */
    @Contract("_ -> new")
    static DatabaseService create(@NotNull Path rootPath) {
        return new DatabaseServiceImpl(rootPath);
    }

    /**
     * Creates a database service configured programmatically from a
     * {@link DatabaseConfig}. Does not require any external configuration file
     * beyond the classpath {@code hibernate.cfg.xml} that declares entity mappings.
     */
    @Contract("_ -> new")
    static DatabaseService create(@NotNull DatabaseConfig config) {
        return new DatabaseServiceImpl(config);
    }

}
