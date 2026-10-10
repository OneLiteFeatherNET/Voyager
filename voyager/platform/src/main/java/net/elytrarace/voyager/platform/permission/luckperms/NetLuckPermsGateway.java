package net.elytrarace.voyager.platform.permission.luckperms;

import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.util.Tristate;

import java.util.Optional;
import java.util.UUID;

/**
 * The production gateway: the only class of the adapter that names the LuckPerms API. It reads the player's cached
 * permission data with LuckPerms' default contextual options; context calculation is a later change.
 */
public final class NetLuckPermsGateway implements LuckPermsGateway {

    @Override
    public Optional<NodeGrant> grant(UUID playerId, String node) {
        User user = LuckPermsProvider.get().getUserManager().getUser(playerId);
        if (user == null) {
            return Optional.empty();
        }
        Tristate result = user.getCachedData().getPermissionData().checkPermission(node);
        // Comparisons rather than a switch on the enum: a switch makes a synthetic class that names Tristate, which the
        // boundary rule would have to be told about.
        if (result == Tristate.TRUE) {
            return Optional.of(NodeGrant.TRUE);
        }
        if (result == Tristate.FALSE) {
            return Optional.of(NodeGrant.FALSE);
        }
        return Optional.of(NodeGrant.UNDEFINED);
    }
}
