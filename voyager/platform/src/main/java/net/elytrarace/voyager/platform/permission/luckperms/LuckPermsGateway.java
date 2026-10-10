package net.elytrarace.voyager.platform.permission.luckperms;

import java.util.Optional;
import java.util.UUID;

/**
 * The seam between {@link LuckPermsPolicy} and the LuckPerms API. The one production implementation is
 * {@code NetLuckPermsGateway}; the policy's tests use a fake, so they never start LuckPerms.
 */
public interface LuckPermsGateway {

    /**
     * Asks LuckPerms about one node for one player, from the player's cached data.
     *
     * @param playerId the player's UUID
     * @param node     the node name
     * @return the grant, or empty when LuckPerms holds no user for the player
     */
    Optional<NodeGrant> grant(UUID playerId, String node);
}
