package net.elytrarace.voyager.platform.lifecycle;

import net.elytrarace.voyager.api.permission.PermissionNode;
import net.elytrarace.voyager.api.permission.PermissionPolicy;
import net.elytrarace.voyager.platform.permission.PlayerSubjects;
import net.elytrarace.voyager.platform.text.Messages;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Player;

import java.util.Objects;

/**
 * {@code /stop}. The console may always stop the server. A player may only when the policy grants
 * {@link PermissionNode#VOYAGER_COMMAND_STOP}; otherwise the player is told so and the server keeps running.
 */
public final class StopCommand extends Command {

    private final PermissionPolicy policy;
    private final ServiceShutdown shutdown;

    public StopCommand(PermissionPolicy policy, ServiceShutdown shutdown) {
        super("stop");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.shutdown = Objects.requireNonNull(shutdown, "shutdown must not be null");
        setDefaultExecutor((sender, context) -> execute(sender));
    }

    void execute(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            shutdown.request();
            return;
        }
        if (policy.allows(PlayerSubjects.of(player), PermissionNode.VOYAGER_COMMAND_STOP)) {
            shutdown.request();
        } else {
            sender.sendMessage(Messages.commandDenied());
        }
    }
}
