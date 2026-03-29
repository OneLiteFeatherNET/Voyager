package net.elytrarace.setup.command;

import net.elytrarace.common.map.MapService;
import net.elytrarace.common.map.model.GuidePointDTO;
import net.elytrarace.common.map.model.PortalDTO;
import net.elytrarace.setup.guide.GuidePointStore;
import net.elytrarace.setup.util.SetupGuard;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;

import java.util.Comparator;

import static org.incendo.cloud.parser.standard.IntegerParser.integerParser;

/**
 * Handles guide point commands for shaping the ideal racing line.
 *
 * <ul>
 * <li>{@code /elytrarace guide} — place a guide point at current position</li>
 * <li>{@code /elytrarace guide delete <orderIndex>} — remove a guide point</li>
 * <li>{@code /elytrarace guide list} — show all guide points</li>
 * </ul>
 */
public class GuideCommand {

    private final MapService mapService;
    private final GuidePointStore guideStore;

    public GuideCommand(MapService mapService, GuidePointStore guideStore) {
        this.mapService = mapService;
        this.guideStore = guideStore;
    }

    /**
     * Place a guide point at the player's current position.
     * Auto-determines which two portals it falls between (by nearest portal centers).
     */
    public void handlePlace(CommandContext<PlayerSource> context) {
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

        if (map.portals().size() < 2) {
            player.sendMessage(Component.translatable("error.guide.need_portals"));
            return;
        }

        var loc = player.getLocation();
        var worldName = player.getWorld().getName();

        // Find the two nearest consecutive portals this point falls between
        var sortedPortals = map.portals().stream()
                .sorted(Comparator.comparingInt(PortalDTO::index))
                .toList();

        // Find nearest portal by distance to center
        int nearestIdx = 0;
        double nearestDist = Double.MAX_VALUE;
        for (int i = 0; i < sortedPortals.size(); i++) {
            var center = sortedPortals.get(i).locations().stream()
                    .filter(l -> l.center())
                    .findFirst()
                    .orElse(null);
            if (center == null) continue;
            double dist = loc.distanceSquared(new Location(player.getWorld(),
                    center.x() + 0.5, center.y() + 0.5, center.z() + 0.5));
            if (dist < nearestDist) {
                nearestDist = dist;
                nearestIdx = i;
            }
        }

        // Determine the segment: between nearestIdx-1 and nearestIdx, or nearestIdx and nearestIdx+1
        int beforeIdx, afterIdx;
        if (nearestIdx == 0) {
            beforeIdx = 0;
            afterIdx = 1;
        } else if (nearestIdx == sortedPortals.size() - 1) {
            beforeIdx = nearestIdx - 1;
            afterIdx = nearestIdx;
        } else {
            // Check which side the player is closer to
            var prevCenter = sortedPortals.get(nearestIdx - 1).locations().stream()
                    .filter(l -> l.center()).findFirst().orElse(null);
            var nextCenter = sortedPortals.get(nearestIdx + 1).locations().stream()
                    .filter(l -> l.center()).findFirst().orElse(null);
            double distPrev = prevCenter != null ?
                    loc.distanceSquared(new Location(player.getWorld(),
                            prevCenter.x() + 0.5, prevCenter.y() + 0.5, prevCenter.z() + 0.5)) : Double.MAX_VALUE;
            double distNext = nextCenter != null ?
                    loc.distanceSquared(new Location(player.getWorld(),
                            nextCenter.x() + 0.5, nextCenter.y() + 0.5, nextCenter.z() + 0.5)) : Double.MAX_VALUE;

            if (distPrev < distNext) {
                beforeIdx = nearestIdx - 1;
                afterIdx = nearestIdx;
            } else {
                beforeIdx = nearestIdx;
                afterIdx = nearestIdx + 1;
            }
        }

        int beforePortalIndex = sortedPortals.get(beforeIdx).index();
        int afterPortalIndex = sortedPortals.get(afterIdx).index();
        int orderIndex = guideStore.nextOrderIndex(worldName, beforePortalIndex, afterPortalIndex);

        var guidePoint = new GuidePointDTO(orderIndex, loc.getX(), loc.getY(), loc.getZ());
        guideStore.addGuidePoint(worldName, guidePoint);

        player.sendActionBar(Component.translatable("guide.placed")
                .arguments(
                        Component.text(orderIndex),
                        Component.text(beforePortalIndex),
                        Component.text(afterPortalIndex)
                ));
    }

    /**
     * Delete a guide point by orderIndex.
     */
    public void handleDelete(CommandContext<PlayerSource> context) {
        var player = context.sender().source();

        if (SetupGuard.getSetupHolder(player).isEmpty()) {
            player.sendActionBar(Component.translatable("error.portal.quick.no_setup"));
            return;
        }

        int orderIndex = context.get("orderIndex");
        var worldName = player.getWorld().getName();

        var removed = guideStore.removeGuidePoint(worldName, orderIndex);
        if (removed != null) {
            player.sendActionBar(Component.translatable("guide.deleted")
                    .arguments(Component.text(orderIndex)));
        } else {
            player.sendMessage(Component.translatable("error.guide.not_found")
                    .arguments(Component.text(orderIndex)));
        }
    }

    /**
     * List all guide points for the current map.
     */
    public void handleList(CommandContext<PlayerSource> context) {
        var player = context.sender().source();

        if (SetupGuard.getSetupHolder(player).isEmpty()) {
            player.sendActionBar(Component.translatable("error.portal.quick.no_setup"));
            return;
        }

        var worldName = player.getWorld().getName();
        var guides = guideStore.getGuidePoints(worldName);

        if (guides.isEmpty()) {
            player.sendMessage(Component.translatable("guide.list.empty"));
            return;
        }

        player.sendMessage(Component.text("Guide Points (" + guides.size() + "):", NamedTextColor.GOLD));
        for (var guide : guides) {
            player.sendMessage(Component.text(
                    "  #" + guide.orderIndex() + " at " +
                            String.format("%.1f, %.1f, %.1f", guide.x(), guide.y(), guide.z()),
                    NamedTextColor.GRAY));
        }
    }

    public static void register(PaperCommandManager<Source> commandManager, MapService mapService,
                                GuidePointStore guideStore) {
        var cmd = new GuideCommand(mapService, guideStore);

        // /elytrarace guide — place at current position
        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("guide")
                .senderType(PlayerSource.class)
                .handler(cmd::handlePlace)
        );

        // /elytrarace guide delete <orderIndex>
        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("guide")
                .literal("delete")
                .required("orderIndex", integerParser(1))
                .senderType(PlayerSource.class)
                .handler(cmd::handleDelete)
        );

        // /elytrarace guide list
        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("guide")
                .literal("list")
                .senderType(PlayerSource.class)
                .handler(cmd::handleList)
        );
    }
}
