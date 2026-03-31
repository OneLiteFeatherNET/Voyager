package net.elytrarace.setup.command;

import net.elytrarace.common.map.MapService;
import net.elytrarace.setup.util.SetupGuard;
import net.elytrarace.setup.util.SetupSuggestions;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;

import static org.incendo.cloud.parser.standard.StringParser.stringParser;

/**
 * Handles {@code /elytrarace map unload <name>} — teleports all players out and unloads the world.
 */
public class MapUnloadCommand {

    private final MapService mapService;
    private final Plugin plugin;

    public MapUnloadCommand(MapService mapService, Plugin plugin) {
        this.mapService = mapService;
        this.plugin = plugin;
    }

    public void handle(CommandContext<PlayerSource> context) {
        var player = context.sender().source();

        if (SetupGuard.getSetupHolder(player).isEmpty()) {
            player.sendMessage(Component.translatable("error.portal.quick.no_setup"));
            return;
        }

        String name = context.get("name");

        var mapOpt = mapService.getMaps().stream()
                .filter(m -> m.name().value().equalsIgnoreCase(name)
                        || m.name().asString().equalsIgnoreCase(name))
                .findFirst();

        if (mapOpt.isEmpty()) {
            player.sendMessage(Component.translatable("error.map.not_found")
                    .arguments(Component.text(name)));
            return;
        }
        var map = mapOpt.get();
        var world = Bukkit.getWorld(map.world());

        if (world == null) {
            player.sendMessage(Component.translatable("error.map.world.not_loaded")
                    .arguments(Component.text(map.world())));
            return;
        }

        var defaultWorld = Bukkit.getWorlds().getFirst();

        // Must run on main thread: teleport + unload
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Evict all players from the world first
            for (var worldPlayer : world.getPlayers()) {
                worldPlayer.teleport(defaultWorld.getSpawnLocation());
            }

            boolean unloaded = Bukkit.unloadWorld(world, true);
            if (unloaded) {
                player.sendActionBar(Component.translatable("map.unload.success")
                        .arguments(Component.text(map.world())));
            } else {
                player.sendMessage(Component.translatable("error.map.unload.failed")
                        .arguments(Component.text(map.world())));
            }
        });
    }

    public static void register(PaperCommandManager<Source> commandManager, MapService mapService, Plugin plugin) {
        var cmd = new MapUnloadCommand(mapService, plugin);
        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("map")
                .literal("unload")
                .required("name", stringParser(), SetupSuggestions.mapNames(mapService))
                .senderType(PlayerSource.class)
                .handler(cmd::handle)
        );
    }
}
