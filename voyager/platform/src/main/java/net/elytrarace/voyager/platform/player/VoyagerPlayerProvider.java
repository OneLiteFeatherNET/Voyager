package net.elytrarace.voyager.platform.player;

import net.elytrarace.voyager.api.permission.PermissionPolicy;
import net.minestom.server.entity.Player;
import net.minestom.server.network.PlayerProvider;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.network.player.PlayerConnection;

/**
 * Creates every player of a Voyager server as a {@link VoyagerPlayer}. A composition root installs it with
 * {@code MinecraftServer.getConnectionManager().setPlayerProvider(...)} before the port binds, so that no player can
 * connect without the permission pointer.
 */
public final class VoyagerPlayerProvider implements PlayerProvider {

    private final PermissionPolicy policy;

    /**
     * @param policy the port every player's permission pointer answers through
     */
    public VoyagerPlayerProvider(PermissionPolicy policy) {
        this.policy = policy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Player createPlayer(PlayerConnection connection, GameProfile gameProfile) {
        return new VoyagerPlayer(connection, gameProfile, policy);
    }
}
