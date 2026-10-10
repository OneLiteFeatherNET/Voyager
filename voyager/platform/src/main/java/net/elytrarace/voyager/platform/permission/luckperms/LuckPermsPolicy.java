package net.elytrarace.voyager.platform.permission.luckperms;

import net.elytrarace.voyager.api.permission.PermissionPolicy;
import net.elytrarace.voyager.api.permission.PermissionSubject;

import java.util.Objects;

/**
 * The policy used when LuckPerms runs. The console is always allowed. A player is allowed a node only when LuckPerms
 * grants it as {@link NodeGrant#TRUE}. No user, an undefined node and an explicit false all deny.
 */
public final class LuckPermsPolicy implements PermissionPolicy {

    private final LuckPermsGateway gateway;

    public LuckPermsPolicy(LuckPermsGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
    }

    @Override
    public boolean allows(PermissionSubject subject, String node) {
        return switch (subject) {
            case PermissionSubject.Console _ -> true;
            case PermissionSubject.Player player -> gateway.grant(player.id(), node)
                    .filter(grant -> grant == NodeGrant.TRUE)
                    .isPresent();
        };
    }
}
