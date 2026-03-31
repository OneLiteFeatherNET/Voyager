package net.elytrarace.setup.command;

import net.elytrarace.common.cup.CupService;
import net.elytrarace.setup.gui.CupListGui;
import net.elytrarace.setup.util.SetupGuard;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;

/**
 * Handles {@code /elytrarace cup} (bare, no subcommand) — opens the Cup Manager GUI.
 */
public class CupListCommand {

    private final CupService cupService;
    private final Plugin plugin;

    public CupListCommand(CupService cupService, Plugin plugin) {
        this.cupService = cupService;
        this.plugin = plugin;
    }

    public void handle(CommandContext<PlayerSource> context) {
        var player = context.sender().source();

        if (SetupGuard.getSetupHolder(player).isEmpty()) {
            player.sendMessage(Component.translatable("error.portal.quick.no_setup"));
            return;
        }

        var cups = cupService.getCups();
        if (cups.isEmpty()) {
            player.sendMessage(Component.translatable("gui.cup.empty"));
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            var gui = new CupListGui(cups);
            gui.open(player);
        });
    }

    public static void register(PaperCommandManager<Source> commandManager, CupService cupService, Plugin plugin) {
        var cmd = new CupListCommand(cupService, plugin);
        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("cup")
                .senderType(PlayerSource.class)
                .handler(cmd::handle)
        );
    }
}
