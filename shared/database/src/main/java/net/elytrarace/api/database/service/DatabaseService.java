package net.elytrarace.api.database.service;

import net.elytrarace.api.database.repository.ElytraPlayerRepository;
import net.elytrarace.api.database.repository.GameResultRepository;
import org.jetbrains.annotations.Contract;

import java.nio.file.Path;
import java.util.Optional;

public sealed interface DatabaseService permits DatabaseServiceImpl {

    String HIBERNATE_CONFIG_FILE_NAME = "hibernate.cfg.xml";

    /**
     * Initializes the database service
     */
    void init();

    /**
     * @return The ElytraPlayerRepository if it was initialized
     */
    Optional<ElytraPlayerRepository> getElytraPlayerRepository();

    /**
     * @return The GameResultRepository if it was initialized
     */
    Optional<GameResultRepository> getGameResultRepository();

    @Contract("_ -> new")
    static DatabaseService create(Path rootPath) {
        return new DatabaseServiceImpl(rootPath);
    }

}
