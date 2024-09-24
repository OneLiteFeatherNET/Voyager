package net.elytrarace.api.database.storage;

import net.elytrarace.api.database.model.DatabaseElytraPlayer;
import net.elytrarace.api.database.repository.ElytraPlayerRepository;
import org.hibernate.SessionFactory;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ElytraPlayerStorage implements ElytraPlayerRepository  {

    private final SessionFactory sessionFactory;

    public ElytraPlayerStorage(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public CompletableFuture<DatabaseElytraPlayer> getElytraPlayerById(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> this.sessionFactory.fromSession(session -> session.get(DatabaseElytraPlayer.class, playerId)));
    }

    @Override
    public CompletableFuture<Void> saveElytraPlayer(DatabaseElytraPlayer elytraPlayer) {
        return CompletableFuture.runAsync(() -> this.sessionFactory.inTransaction(session -> session.persist(elytraPlayer)));
    }

    @Override
    public CompletableFuture<Void> deleteElytraPlayer(DatabaseElytraPlayer elytraPlayer) {
        return CompletableFuture.runAsync(() -> this.sessionFactory.inTransaction(session -> session.remove(elytraPlayer)));
    }

    @Override
    public CompletableFuture<Void> updateElytraPlayer(DatabaseElytraPlayer elytraPlayer) {
        return CompletableFuture.runAsync(() -> this.sessionFactory.inTransaction(session -> session.merge(elytraPlayer)));
    }
}
