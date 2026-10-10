package net.elytrarace.voyager.platform.permission;

import net.elytrarace.voyager.api.permission.PermissionSubject;
import net.minestom.server.command.CommandSender;
import net.minestom.server.entity.Player;

/**
 * Turns a Minestom command sender into the permission subject a policy asks about: a player, or else the console.
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

    /**
     * Reads the subject of whoever typed a command. Every sender that is not a player is the console.
     *
     * @param sender the command sender
     * @return the player's subject, or the console subject
     */
    public static PermissionSubject subjectOf(CommandSender sender) {
        return sender instanceof Player player ? of(player) : new PermissionSubject.Console();
    }
}
