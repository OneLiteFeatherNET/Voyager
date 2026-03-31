package net.elytrarace.setup.command;

import net.elytrarace.common.map.MapService;
import net.elytrarace.common.map.model.LocationDTO;
import net.elytrarace.common.map.model.PortalDTO;
import net.elytrarace.setup.testfly.TestflyManager;
import net.elytrarace.setup.testfly.TestflySession;
import net.elytrarace.setup.util.SetupGuard;
import net.elytrarace.setup.util.SetupSuggestions;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;

import java.util.ArrayList;
import java.util.Comparator;

import static org.incendo.cloud.parser.standard.IntegerParser.integerParser;

/**
 * Handles {@code /elytrarace portal testfly [startIndex]} and
 * {@code /elytrarace portal testfly stop}.
 */
public class PortalTestflyCommand {

    private final MapService mapService;
    private final TestflyManager testflyManager;
    private final Plugin plugin;

    public PortalTestflyCommand(MapService mapService, TestflyManager testflyManager, Plugin plugin) {
        this.mapService = mapService;
        this.testflyManager = testflyManager;
        this.plugin = plugin;
    }

    public void handleStart(CommandContext<PlayerSource> context) {
        var player = context.sender().source();

        if (SetupGuard.getSetupHolder(player).isEmpty()) {
            player.sendMessage(Component.translatable("error.portal.quick.no_setup"));
            return;
        }

        if (testflyManager.isFlying(player.getUniqueId())) {
            player.sendMessage(Component.translatable("error.testfly.already_flying"));
            return;
        }

        var mapOpt = SetupGuard.getMapForWorld(mapService, player.getWorld());
        if (mapOpt.isEmpty()) {
            player.sendMessage(Component.translatable("error.portal.quick.no_map"));
            return;
        }
        var map = mapOpt.get();

        if (map.portals().isEmpty()) {
            player.sendMessage(Component.translatable("error.testfly.no_portals"));
            return;
        }

        // Sort portals by index
        var sortedPortals = new ArrayList<>(map.portals().stream()
                .sorted(Comparator.comparingInt(PortalDTO::index))
                .toList());

        // Find starting portal — use optional startIndex argument if provided
        int startFromIndex = context.<Integer>optional("startIndex").orElse(0);
        var startPortal = sortedPortals.stream()
                .filter(p -> p.index() == startFromIndex)
                .findFirst()
                .orElse(null);
        if (startFromIndex != 0 && startPortal == null) {
            player.sendMessage(Component.translatable("error.testfly.invalid_start_index")
                    .arguments(Component.text(startFromIndex)));
            return;
        }
        var firstPortal = startPortal != null ? startPortal : sortedPortals.getFirst();

        // Save inventory
        var savedInventory = player.getInventory().getContents().clone();
        var savedArmor = player.getInventory().getArmorContents().clone();

        // Create session
        var session = new TestflySession(player.getUniqueId(), sortedPortals, savedInventory, savedArmor);
        testflyManager.addSession(session);

        // Must run on main thread: game mode change, inventory, teleport
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.getInventory().clear();
            player.getInventory().setChestplate(new ItemStack(Material.ELYTRA));
            player.getInventory().setItem(0, new ItemStack(Material.FIREWORK_ROCKET, 64));
            player.setGameMode(GameMode.ADVENTURE);

            var center = firstPortal.locations().stream()
                    .filter(LocationDTO::center)
                    .findFirst()
                    .orElse(firstPortal.locations().getFirst());

            var spawnLoc = new Location(player.getWorld(),
                    center.x() + 0.5, center.y() + 5, center.z() + 0.5);
            player.teleport(spawnLoc);

            player.sendMessage(Component.translatable("testfly.start")
                    .arguments(Component.text(sortedPortals.size())));
            player.sendMessage(Component.translatable("testfly.start.hint"));
        });
    }

    public void handleStop(CommandContext<PlayerSource> context) {
        var player = context.sender().source();

        var sessionOpt = testflyManager.endFlight(player.getUniqueId());
        if (sessionOpt.isEmpty()) {
            player.sendMessage(Component.translatable("error.testfly.not_flying"));
            return;
        }

        var session = sessionOpt.get();
        player.sendMessage(Component.empty());
        player.sendMessage(Component.translatable("testfly.stop.header"));
        player.sendMessage(Component.translatable("testfly.stop.time")
                .arguments(Component.text(session.elapsedFormatted())));
        player.sendMessage(Component.translatable("testfly.stop.portals")
                .arguments(Component.text(session.hitCount()), Component.text(session.totalPortals())));

        var missed = session.missedPortalIndices();
        if (!missed.isEmpty()) {
            player.sendMessage(Component.translatable("testfly.stop.missed")
                    .arguments(Component.text(String.join(", #", missed.stream().map(String::valueOf).toList()))));
        }
        player.sendMessage(Component.empty());
    }

    public static void register(PaperCommandManager<Source> commandManager, MapService mapService,
                                TestflyManager testflyManager, Plugin plugin) {
        var cmd = new PortalTestflyCommand(mapService, testflyManager, plugin);

        // /elytrarace portal testfly (start, from first portal)
        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("portal")
                .literal("testfly")
                .senderType(PlayerSource.class)
                .handler(cmd::handleStart)
        );

        // /elytrarace portal testfly <startIndex> (start from specific portal)
        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("portal")
                .literal("testfly")
                .optional("startIndex", integerParser(1), SetupSuggestions.portalIndices(mapService))
                .senderType(PlayerSource.class)
                .handler(cmd::handleStart)
        );

        // /elytrarace portal testfly stop
        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("portal")
                .literal("testfly")
                .literal("stop")
                .senderType(PlayerSource.class)
                .handler(cmd::handleStop)
        );
    }
}
