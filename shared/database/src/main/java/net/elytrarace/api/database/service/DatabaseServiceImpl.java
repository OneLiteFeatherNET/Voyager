package net.elytrarace.api.database.service;

import net.elytrarace.api.database.repository.ElytraPlayerRepository;
import net.elytrarace.api.database.storage.ElytraPlayerStorage;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

import java.nio.file.Path;
import java.util.Optional;

class DatabaseServiceImpl implements DatabaseService {

    private final Path rootPath;
    private ElytraPlayerRepository elytraPlayerRepository;

    DatabaseServiceImpl(Path rootPath) {
        this.rootPath = rootPath;
    }

    @Override
    public void init() {
        SessionFactory sessionFactory = new Configuration().configure().configure(rootPath.resolve("hibernate.cfg.xml").toFile()).buildSessionFactory();
        this.elytraPlayerRepository = new ElytraPlayerStorage(sessionFactory);
    }

    @Override
    public Optional<ElytraPlayerRepository> getElytraPlayerRepository() {
        return Optional.ofNullable(this.elytraPlayerRepository);
    }
}
