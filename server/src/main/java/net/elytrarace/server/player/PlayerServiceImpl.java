package net.elytrarace.server.player;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.InstanceContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Default implementation of {@link PlayerService}.
 * <p>
 * Manages player join/leave lifecycle and cross-instance teleportation.
 * The lobby instance is injected at construction time and used as the default
 * spawn location for joining players.
 */
public final class PlayerServiceImpl implements PlayerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerServiceImpl.class);
    private static final Pos LOBBY_SPAWN = new Pos(0, 2, 0);

    private final InstanceContainer lobbyInstance;

    /**
     * Creates a new player service backed by the given lobby instance.
     *
     * @param lobbyInstance the lobby instance where players spawn on join
     */
    public PlayerServiceImpl(InstanceContainer lobbyInstance) {
        this.lobbyInstance = Objects.requireNonNull(lobbyInstance, "lobbyInstance must not be null");
    }

    @Override
    public void onPlayerJoin(Player player) {
        LOGGER.info("Player {} ({}) joined the server", player.getUsername(), player.getUuid());
        player.setGameMode(GameMode.ADVENTURE);
        player.setRespawnPoint(LOBBY_SPAWN);
    }

    @Override
    public void onPlayerLeave(Player player) {
        LOGGER.info("Player {} ({}) left the server", player.getUsername(), player.getUuid());
        // Future: remove from active game session, persist stats, etc.
    }

    @Override
    public void teleportToInstance(Player player, InstanceContainer instance, Pos spawnPos) {
        Objects.requireNonNull(player, "player must not be null");
        Objects.requireNonNull(instance, "instance must not be null");
        Objects.requireNonNull(spawnPos, "spawnPos must not be null");

        LOGGER.debug("Teleporting player {} to instance {} at {}",
                player.getUsername(), instance.getUuid(), spawnPos);

        player.setInstance(instance, spawnPos);
    }

    @Override
    public Collection<Player> getOnlinePlayers() {
        return MinecraftServer.getConnectionManager().getOnlinePlayers();
    }

    @Override
    public Optional<Player> getPlayer(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid must not be null");
        return Optional.ofNullable(MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid));
    }

    /**
     * Returns the lobby instance used as the default spawn location.
     *
     * @return the lobby instance
     */
    public InstanceContainer getLobbyInstance() {
        return lobbyInstance;
    }
}
