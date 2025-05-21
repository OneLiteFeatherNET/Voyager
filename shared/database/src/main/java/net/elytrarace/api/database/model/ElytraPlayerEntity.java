package net.elytrarace.api.database.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.util.UUID;

@Entity
public class ElytraPlayerEntity {

    @Id
    private UUID playerId;

    public ElytraPlayerEntity(UUID playerId) {
        this.playerId = playerId;
    }

    public ElytraPlayerEntity() {
    }

    public UUID getPlayerId() {
        return playerId;
    }
}
