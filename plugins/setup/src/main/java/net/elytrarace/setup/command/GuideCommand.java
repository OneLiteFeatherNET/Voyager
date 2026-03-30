package net.elytrarace.setup.command;

import net.elytrarace.common.map.MapService;
import net.elytrarace.common.map.model.GuidePointDTO;
import net.elytrarace.common.map.model.PortalDTO;
import net.elytrarace.common.guide.GuidePointStore;
import net.elytrarace.setup.util.SetupGuard;
import net.elytrarace.setup.util.SetupSuggestions;
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
            player.sendMessage(Component.translatable("error.portal.quick.no_setup"));
            return;
        }

        var mapOpt = SetupGuard.getMapForWorld(mapService, player.getWorld());
        if (mapOpt.isEmpty()) {
            player.sendMessage(Component.translatable("error.portal.quick.no_map"));
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
     * Place a guide point explicitly between two specified portal indices.
     * Usage: /elytrarace guide between <portalA> <portalB>
     */
    public void handlePlaceBetween(CommandContext<PlayerSource> context) {
        var player = context.sender().source();

        if (SetupGuard.getSetupHolder(player).isEmpty()) {
            player.sendMessage(Component.translatable("error.portal.quick.no_setup"));
            return;
        }

        var mapOpt = SetupGuard.getMapForWorld(mapService, player.getWorld());
        if (mapOpt.isEmpty()) {
            player.sendMessage(Component.translatable("error.portal.quick.no_map"));
            return;
        }
        var map = mapOpt.get();

        int portalA = context.get("portalA");
        int portalB = context.get("portalB");

        // Validate both portals exist
        var hasA = map.portals().stream().anyMatch(p -> p.index() == portalA);
        var hasB = map.portals().stream().anyMatch(p -> p.index() == portalB);
        if (!hasA || !hasB) {
            player.sendMessage(Component.translatable("error.guide.portal_not_found")
                    .arguments(Component.text(hasA ? portalB : portalA)));
            return;
        }

        // Ensure A < B
        int before = Math.min(portalA, portalB);
        int after = Math.max(portalA, portalB);

        var loc = player.getLocation();
        var worldName = player.getWorld().getName();
        int orderIndex = guideStore.nextOrderIndex(worldName, before, after);

        var guidePoint = new GuidePointDTO(orderIndex, loc.getX(), loc.getY(), loc.getZ());
        guideStore.addGuidePoint(worldName, guidePoint);

        player.sendActionBar(Component.translatable("guide.placed")
                .arguments(
                        Component.text(orderIndex),
                        Component.text(before),
                        Component.text(after)
                ));
    }

    /**
     * Delete a guide point by orderIndex.
     */
    public void handleDelete(CommandContext<PlayerSource> context) {
        var player = context.sender().source();

        if (SetupGuard.getSetupHolder(player).isEmpty()) {
            player.sendMessage(Component.translatable("error.portal.quick.no_setup"));
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
            player.sendMessage(Component.translatable("error.portal.quick.no_setup"));
            return;
        }

        var worldName = player.getWorld().getName();
        var guides = guideStore.getGuidePoints(worldName);

        if (guides.isEmpty()) {
            player.sendMessage(Component.translatable("guide.list.empty"));
            return;
        }

        player.sendMessage(Component.translatable("guide.list.header").arguments(Component.text(guides.size())));
        for (var guide : guides) {
            player.sendMessage(Component.translatable("guide.list.entry")
                    .arguments(Component.text(guide.orderIndex()),
                            Component.text(String.format("%.1f", guide.x())),
                            Component.text(String.format("%.1f", guide.y())),
                            Component.text(String.format("%.1f", guide.z()))));
        }
    }

    /**
     * Move a guide point to the player's current position.
     */
    public void handleMove(CommandContext<PlayerSource> context) {
        var player = context.sender().source();

        if (SetupGuard.getSetupHolder(player).isEmpty()) {
            player.sendMessage(Component.translatable("error.portal.quick.no_setup"));
            return;
        }

        int orderIndex = context.get("orderIndex");
        var loc = player.getLocation();
        var worldName = player.getWorld().getName();

        var moved = guideStore.moveGuidePoint(worldName, orderIndex, loc.getX(), loc.getY(), loc.getZ());
        if (moved != null) {
            player.sendActionBar(Component.translatable("guide.moved")
                    .arguments(Component.text(orderIndex)));
        } else {
            player.sendMessage(Component.translatable("error.guide.not_found")
                    .arguments(Component.text(orderIndex)));
        }
    }

    /**
     * Teleport to a guide point.
     */
    public void handleTp(CommandContext<PlayerSource> context) {
        var player = context.sender().source();

        if (SetupGuard.getSetupHolder(player).isEmpty()) {
            player.sendMessage(Component.translatable("error.portal.quick.no_setup"));
            return;
        }

        int orderIndex = context.get("orderIndex");
        var worldName = player.getWorld().getName();

        var guide = guideStore.getGuidePoints(worldName).stream()
                .filter(g -> g.orderIndex() == orderIndex)
                .findFirst()
                .orElse(null);

        if (guide == null) {
            player.sendMessage(Component.translatable("error.guide.not_found")
                    .arguments(Component.text(orderIndex)));
            return;
        }

        player.teleport(new org.bukkit.Location(player.getWorld(), guide.x(), guide.y(), guide.z()));
        player.sendActionBar(Component.translatable("guide.tp")
                .arguments(Component.text(orderIndex)));
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

        // /elytrarace guide between <portalA> <portalB> — place between specific portals
        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("guide")
                .literal("between")
                .required("portalA", integerParser(1), SetupSuggestions.portalIndices(mapService))
                .required("portalB", integerParser(1), SetupSuggestions.portalIndices(mapService))
                .senderType(PlayerSource.class)
                .handler(cmd::handlePlaceBetween)
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

        // /elytrarace guide move <orderIndex> — move to player position
        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("guide")
                .literal("move")
                .required("orderIndex", integerParser(1))
                .senderType(PlayerSource.class)
                .handler(cmd::handleMove)
        );

        // /elytrarace guide tp <orderIndex> — teleport to guide point
        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("guide")
                .literal("tp")
                .required("orderIndex", integerParser(1))
                .senderType(PlayerSource.class)
                .handler(cmd::handleTp)
        );
    }
}
