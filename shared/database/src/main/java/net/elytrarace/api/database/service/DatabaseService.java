package net.elytrarace.api.database.service;

import net.elytrarace.api.database.repository.ElytraPlayerRepository;
import org.jetbrains.annotations.Contract;

import java.nio.file.Path;
import java.util.Optional;

public interface DatabaseService {

    /**
     * Initializes the database service
     */
    void init();

    /**
     * @return The ElytraPlayerRepository if it was initialized
     */
    Optional<ElytraPlayerRepository> getElytraPlayerRepository();

    @Contract("_ -> new")
    static DatabaseService create(Path rootPath) {
        return new DatabaseServiceImpl(rootPath);
    }

}
