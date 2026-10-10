package net.elytrarace.voyager.server.command;

import net.minestom.server.command.CommandSender;
import net.minestom.server.command.ConsoleSender;
import net.minestom.server.entity.Player;

/**
 * Who may run {@code /race reload}.
 *
 * <p>Minestom 26.2 has no named permission API: a {@link CommandSender} carries no permission set, and a
 * {@link Player} carries only a numeric operator level from 0 to 4. So the gate is the existing one. The console
 * is always allowed, because whoever can start the server can edit its files. A player needs
 * {@link #REQUIRED_LEVEL}, the full operator level. Nothing in this server grants a level yet, so today only the
 * console can reload; the how-to says so.
 */
public final class ReloadPermission {

    /** The operator level a player needs. 4 is the highest level Minestom allows and what {@code /op} grants. */
    public static final int REQUIRED_LEVEL = 4;

    private ReloadPermission() {
    }

    /**
     * @param sender whoever typed the command
     * @return whether the sender may reload the catalogue
     */
    public static boolean mayReload(CommandSender sender) {
        if (sender instanceof ConsoleSender) {
            return true;
        }
        return sender instanceof Player player && player.getPermissionLevel() >= REQUIRED_LEVEL;
    }
}
