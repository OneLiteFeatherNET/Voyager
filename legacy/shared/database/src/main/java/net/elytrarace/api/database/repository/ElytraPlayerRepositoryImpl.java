package net.elytrarace.api.database.repository;

import net.elytrarace.api.database.entity.ElytraPlayerEntity;
import org.hibernate.SessionFactory;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class ElytraPlayerRepositoryImpl implements ElytraPlayerRepository  {

    private final SessionFactory sessionFactory;

    public ElytraPlayerRepositoryImpl(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public CompletableFuture<ElytraPlayerEntity> getElytraPlayerById(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> this.sessionFactory.fromSession(session -> session.get(ElytraPlayerEntity.class, playerId)));
    }

    @Override
    public CompletableFuture<Void> saveElytraPlayer(ElytraPlayerEntity elytraPlayer) {
        return CompletableFuture.runAsync(() -> this.sessionFactory.inTransaction(session -> session.persist(elytraPlayer)));
    }

    @Override
    public CompletableFuture<Void> deleteElytraPlayer(ElytraPlayerEntity elytraPlayer) {
        return CompletableFuture.runAsync(() -> this.sessionFactory.inTransaction(session -> session.remove(elytraPlayer)));
    }

    @Override
    public CompletableFuture<Void> updateElytraPlayer(ElytraPlayerEntity elytraPlayer) {
        return CompletableFuture.runAsync(() -> this.sessionFactory.inTransaction(session -> session.merge(elytraPlayer)));
    }
}
