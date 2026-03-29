package net.elytrarace.setup.command;

import com.fastasyncworldedit.core.regions.PolyhedralRegion;
import net.elytrarace.common.builder.MapDTOBuilder;
import net.elytrarace.common.builder.PortalDTOBuilder;
import net.elytrarace.common.map.MapService;
import net.elytrarace.common.map.model.FilePortalDTO;
import net.elytrarace.setup.util.FaweHelper;
import net.elytrarace.setup.util.SetupGuard;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.util.sender.PlayerSource;

import java.util.TreeSet;

/**
 * Handles {@code /elytrarace portal} — saves the player's current FAWE PolyhedralRegion
 * as the next portal in the map, with auto-assigned index.
 */
public class PortalCommand {

    private final MapService mapService;

    public PortalCommand(MapService mapService) {
        this.mapService = mapService;
    }

    public void handle(CommandContext<PlayerSource> context) {
        var player = context.sender().source();

        // 1. Verify setup mode
        var holderOpt = SetupGuard.getSetupHolder(player);
        if (holderOpt.isEmpty()) {
            player.sendActionBar(Component.translatable("error.portal.quick.no_setup"));
            return;
        }

        // 2. Verify world is set up and find map
        var world = player.getWorld();
        if (!SetupGuard.isSetupWorld(world)) {
            player.sendActionBar(Component.translatable("error.portal.quick.no_setup_world")
                    .arguments(Component.text(world.getName())));
            return;
        }

        var mapOpt = SetupGuard.getMapForWorld(mapService, world);
        if (mapOpt.isEmpty()) {
            player.sendActionBar(Component.translatable("error.portal.quick.no_map"));
            return;
        }
        var map = mapOpt.get();

        // 3. Get FAWE PolyhedralRegion
        var selectorOpt = FaweHelper.getPolyhedralSelector(player);
        if (selectorOpt.isEmpty()) {
            player.sendMessage(Component.translatable("error.portal.quick.wrong_region_type"));
            FaweHelper.resetToPolyhedralSelector(player);
            return;
        }

        var selector = selectorOpt.get();
        if (selector.getVertices().size() <= 3) {
            player.sendMessage(Component.translatable("error.portal.quick.too_few_vertices")
                    .arguments(Component.text(selector.getVertices().size())));
            return;
        }

        // 4. Extract region data
        var region = (PolyhedralRegion) selector.getRegion();
        var locations = FaweHelper.extractLocations(region);

        // 5. Auto-assign next index
        int nextIndex = FaweHelper.nextPortalIndex(map.portals());

        // 6. Build portal and updated map
        FilePortalDTO portal = PortalDTOBuilder.create()
                .index(nextIndex)
                .locations(locations)
                .build();

        var portals = new TreeSet<>(map.portals());
        portals.add(portal);
        var newMap = MapDTOBuilder.create().from(map).portals(portals).build();

        // 7. Save
        mapService.updateMap(newMap).thenCompose(success -> {
            if (success) {
                player.sendActionBar(Component.translatable("success.portal.quick")
                        .arguments(Component.text(nextIndex), Component.text(portals.size())));
                return mapService.saveMaps();
            }
            player.sendMessage(Component.translatable("error.portal.quick.save_failed")
                    .arguments(map.displayName(), Component.text(nextIndex)));
            return null;
        });

        // 8. Clear FAWE selection for next ring
        selector.clear();
    }

    /**
     * Registers this command with the given command manager and builder.
     */
    public static void register(
            org.incendo.cloud.paper.PaperCommandManager<org.incendo.cloud.paper.util.sender.Source> commandManager,
            MapService mapService
    ) {
        var cmd = new PortalCommand(mapService);
        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("portal")
                .senderType(PlayerSource.class)
                .handler(cmd::handle)
        );
    }
}
