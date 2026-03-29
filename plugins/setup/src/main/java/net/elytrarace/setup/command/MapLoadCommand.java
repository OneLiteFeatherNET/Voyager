package net.elytrarace.setup.command;

import net.elytrarace.setup.util.SetupGuard;
import net.elytrarace.setup.util.SetupSuggestions;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.Plugin;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;

import java.nio.file.Files;

import static org.incendo.cloud.parser.standard.StringParser.stringParser;

/**
 * Handles {@code /elytrarace map load <worldFolder>} — loads an Anvil world folder
 * into the server using VoidGen and teleports the builder there.
 */
public class MapLoadCommand {

    private final Plugin plugin;

    public MapLoadCommand(Plugin plugin) {
        this.plugin = plugin;
    }

    public void handle(CommandContext<PlayerSource> context) {
        var player = context.sender().source();

        if (SetupGuard.getSetupHolder(player).isEmpty()) {
            player.sendActionBar(Component.translatable("error.portal.quick.no_setup"));
            return;
        }

        String worldFolder = context.get("worldFolder");

        // Check if already loaded
        World existing = Bukkit.getWorld(worldFolder);
        if (existing != null) {
            player.teleport(existing.getSpawnLocation());
            player.sendActionBar(Component.translatable("map.load.already_loaded")
                    .arguments(Component.text(worldFolder)));
            return;
        }

        // Check if the folder exists on disk
        var worldPath = Bukkit.getWorldContainer().toPath().resolve(worldFolder);
        if (!Files.isDirectory(worldPath)) {
            player.sendMessage(Component.translatable("error.map.load.not_found")
                    .arguments(Component.text(worldFolder)));
            return;
        }

        player.sendActionBar(Component.translatable("map.load.loading")
                .arguments(Component.text(worldFolder)));

        // Must create world on the main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            var creator = new WorldCreator(worldFolder)
                    .generator("VoidGen")
                    .environment(World.Environment.NORMAL);
            World world = Bukkit.createWorld(creator);

            if (world == null) {
                player.sendMessage(Component.translatable("error.map.load.failed")
                        .arguments(Component.text(worldFolder)));
                return;
            }

            player.teleport(world.getSpawnLocation());
            player.sendActionBar(Component.translatable("map.load.success")
                    .arguments(Component.text(worldFolder)));
        });
    }

    public static void register(PaperCommandManager<Source> commandManager, Plugin plugin) {
        var cmd = new MapLoadCommand(plugin);
        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("map")
                .literal("load")
                .required("worldFolder", stringParser(), SetupSuggestions.worldFolders())
                .senderType(PlayerSource.class)
                .handler(cmd::handle)
        );
    }
}
