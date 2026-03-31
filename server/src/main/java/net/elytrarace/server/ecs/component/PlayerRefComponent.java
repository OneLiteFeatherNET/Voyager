package net.elytrarace.server.ecs.component;

import net.elytrarace.common.ecs.Component;
import net.minestom.server.entity.Player;

import java.util.UUID;

/**
 * Holds a reference to the Minestom {@link Player} and their UUID.
 * This component bridges the ECS world to the Minestom server.
 */
public class PlayerRefComponent implements Component {

    private final UUID playerId;
    private final Player player;

    public PlayerRefComponent(UUID playerId, Player player) {
        this.playerId = playerId;
        this.player = player;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Player getPlayer() {
        return player;
    }
}
