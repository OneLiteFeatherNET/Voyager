package net.elytrarace.voyager.server.command;

import net.elytrarace.voyager.platform.lobby.WaitingRoom;
import net.elytrarace.voyager.platform.text.Messages;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Player;

/**
 * {@code /leave}, with {@code /hub} as its alias: a waiting racer goes to the network hub, as the leave door in the lobby does.
 *
 * <p>Refused with a translated line when the sender is not a racer waiting in the lobby, for instance a racer in a map, and the
 * refusal leaves the racer where they are. Nothing is recorded: it is the same hand-off as the door, see
 * {@link WaitingRoom#leaveToHub}.
 */
public final class LeaveCommand extends Command {

    /**
     * @param room the waiting room, which decides whether the sender may leave and sends them to the hub
     */
    public LeaveCommand(WaitingRoom room) {
        super("leave", "hub");
        setDefaultExecutor((sender, context) -> {
            if (sender instanceof Player racer && room.leaveToHub(racer)) {
                return;
            }
            sender.sendMessage(Messages.leaveRefused());
        });
    }
}
