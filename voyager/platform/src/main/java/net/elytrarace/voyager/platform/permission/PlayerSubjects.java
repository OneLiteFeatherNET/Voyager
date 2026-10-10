package net.elytrarace.voyager.platform.permission;

import net.elytrarace.voyager.api.permission.PermissionSubject;
import net.minestom.server.entity.Player;

/**
 * Turns a connected Minestom player into the permission subject a policy asks about.
 */
public final class PlayerSubjects {

    private PlayerSubjects() {
    }

    /**
     * Reads the player's UUID and operator level at the moment of the call.
     *
     * @param player the connected player
     * @return the subject for that player
     */
    public static PermissionSubject.Player of(Player player) {
        return new PermissionSubject.Player(player.getUuid(), player.getPermissionLevel());
    }
}
