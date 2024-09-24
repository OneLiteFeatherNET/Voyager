package net.elytrarace.api.database.service;

import net.elytrarace.api.database.repository.ElytraPlayerRepository;
import net.elytrarace.api.database.storage.ElytraPlayerStorage;
import net.elytrarace.common.utils.ThreadHelper;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Optional;

class DatabaseServiceImpl implements DatabaseService, ThreadHelper {

    private final Path rootPath;
    private ElytraPlayerRepository elytraPlayerRepository;

    DatabaseServiceImpl(@NotNull Path rootPath) {
        this.rootPath = rootPath;
    }

    @Override
    public void init() {
        SessionFactory sessionFactory = syncThreadForServiceLoader(this::createSessionFactory);
        this.elytraPlayerRepository = new ElytraPlayerStorage(sessionFactory);
    }

    private SessionFactory createSessionFactory() {
        return new Configuration().configure().configure(rootPath.resolve("hibernate.cfg.xml").toFile()).buildSessionFactory();
    }

    @Override
    public Optional<ElytraPlayerRepository> getElytraPlayerRepository() {
        return Optional.ofNullable(this.elytraPlayerRepository);
    }
}
