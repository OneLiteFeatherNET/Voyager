package net.elytrarace.api.database.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.util.UUID;

@Entity
public class DatabaseElytraPlayer {

    @Id
    private UUID playerId;

    public DatabaseElytraPlayer(UUID playerId) {
        this.playerId = playerId;
    }

    public DatabaseElytraPlayer() {
    }

    public UUID getPlayerId() {
        return playerId;
    }
}
