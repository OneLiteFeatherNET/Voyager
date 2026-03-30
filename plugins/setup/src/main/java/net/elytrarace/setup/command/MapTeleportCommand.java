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
 * Handles {@code /elytrarace map tp <name>} — teleports the builder to a map's world.
 */
public class MapTeleportCommand {

    private final MapService mapService;
    private final Plugin plugin;

    public MapTeleportCommand(MapService mapService, Plugin plugin) {
        this.mapService = mapService;
        this.plugin = plugin;
    }

    public void handle(CommandContext<PlayerSource> context) {
        var player = context.sender().source();

        if (SetupGuard.getSetupHolder(player).isEmpty()) {
            player.sendActionBar(Component.translatable("error.portal.quick.no_setup"));
            return;
        }

        String mapName = context.get("name");

        // Find map by name value (without namespace prefix)
        var mapOpt = mapService.getMaps().stream()
                .filter(m -> m.name().value().equalsIgnoreCase(mapName)
                        || m.name().asString().equalsIgnoreCase(mapName))
                .findFirst();

        if (mapOpt.isEmpty()) {
            player.sendMessage(Component.translatable("error.map.not_found")
                    .arguments(Component.text(mapName)));
            return;
        }
        var map = mapOpt.get();

        // Find the Bukkit world
        var world = Bukkit.getWorld(map.world());
        if (world == null) {
            player.sendMessage(Component.translatable("error.map.world.not_found")
                    .arguments(Component.text(map.world())));
            return;
        }

        // Teleport to world spawn — must run on main thread
        var spawn = world.getSpawnLocation();
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.teleport(spawn);
            player.sendActionBar(Component.translatable("map.tp.success")
                    .arguments(map.displayName(), Component.text(map.world())));
        });
    }

    public static void register(PaperCommandManager<Source> commandManager, MapService mapService, Plugin plugin) {
        var cmd = new MapTeleportCommand(mapService, plugin);
        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("map")
                .literal("tp")
                .required("name", stringParser(), SetupSuggestions.mapNames(mapService))
                .senderType(PlayerSource.class)
                .handler(cmd::handle)
        );
    }
}
