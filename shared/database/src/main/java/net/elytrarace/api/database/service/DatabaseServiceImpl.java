package net.elytrarace.api.database.service;

import net.elytrarace.api.database.repository.ElytraPlayerRepository;
import net.elytrarace.api.database.repository.GameResultRepository;
import net.elytrarace.common.utils.ThreadHelper;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Optional;

final class DatabaseServiceImpl implements DatabaseService, ThreadHelper {

    private final Path rootPath;
    private ElytraPlayerRepository elytraPlayerRepository;
    private GameResultRepository gameResultRepository;

    DatabaseServiceImpl(@NotNull Path rootPath) {
        this.rootPath = rootPath;
    }

    @Override
    public void init() {
        SessionFactory sessionFactory = syncThreadForServiceLoader(this::createSessionFactory);
        this.elytraPlayerRepository = ElytraPlayerRepository.createInstance(sessionFactory);
        this.gameResultRepository = GameResultRepository.createInstance(sessionFactory);
    }

    private SessionFactory createSessionFactory() {
        return new Configuration().configure().configure(rootPath.resolve(HIBERNATE_CONFIG_FILE_NAME).toFile()).buildSessionFactory();
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
