package net.elytrarace.voyager.platform.permission;

import net.elytrarace.voyager.api.permission.PermissionPolicy;
import net.elytrarace.voyager.api.permission.PermissionSubject;

/**
 * The policy in use when LuckPerms is absent. The console is allowed every node. A player is allowed every node only at
 * operator level 4, the same bar {@code /race reload} already had, and is denied everything below it. It never grants a
 * player more than {@code /op} does (ADR-0024).
 */
public final class LevelPermissionPolicy implements PermissionPolicy {

    @Override
    public boolean allows(PermissionSubject subject, String node) {
        return switch (subject) {
            case PermissionSubject.Console _ -> true;
            case PermissionSubject.Player player -> player.operatorLevel() == PermissionSubject.MAX_OPERATOR_LEVEL;
        };
    }
}
