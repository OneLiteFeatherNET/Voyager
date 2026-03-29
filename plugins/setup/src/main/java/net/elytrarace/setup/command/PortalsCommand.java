package net.elytrarace.setup.command;

import net.elytrarace.common.map.MapService;
import net.elytrarace.setup.gui.PortalManagerGui;
import net.elytrarace.setup.util.SetupGuard;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;

/**
 * Handles {@code /elytrarace portals} — opens the Portal Manager GUI.
 */
public class PortalsCommand {

    private final MapService mapService;
    private final Plugin plugin;

    public PortalsCommand(MapService mapService, Plugin plugin) {
        this.mapService = mapService;
        this.plugin = plugin;
    }

    public void handle(CommandContext<PlayerSource> context) {
        var player = context.sender().source();

        if (SetupGuard.getSetupHolder(player).isEmpty()) {
            player.sendActionBar(Component.translatable("error.portal.quick.no_setup"));
            return;
        }

        var mapOpt = SetupGuard.getMapForWorld(mapService, player.getWorld());
        if (mapOpt.isEmpty()) {
            player.sendActionBar(Component.translatable("error.portal.quick.no_map"));
            return;
        }
        var map = mapOpt.get();

        if (map.portals().isEmpty()) {
            player.sendMessage(Component.translatable("gui.portal.empty"));
            return;
        }

        // Must open inventory on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            var gui = new PortalManagerGui(map.portals(), map.displayName());
            gui.open(player);
        });
    }

    public static void register(PaperCommandManager<Source> commandManager, MapService mapService, Plugin plugin) {
        var cmd = new PortalsCommand(mapService, plugin);
        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("portals")
                .senderType(PlayerSource.class)
                .handler(cmd::handle)
        );
    }
}
