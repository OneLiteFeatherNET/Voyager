package net.elytrarace.setup.command;

import net.elytrarace.common.guide.GuidePointStore;
import net.elytrarace.common.map.MapService;
import net.elytrarace.setup.preview.ParticlePreviewManager;
import net.elytrarace.setup.util.SetupGuard;
import net.kyori.adventure.text.Component;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;

/**
 * Handles {@code /elytrarace map status} — prints a compact overview of the
 * current map: portal count, guide points, cup assignment, and active previews.
 */
public class MapStatusCommand {

    private final MapService mapService;
    private final GuidePointStore guidePointStore;
    private final ParticlePreviewManager previewManager;

    public MapStatusCommand(MapService mapService, GuidePointStore guidePointStore,
                            ParticlePreviewManager previewManager) {
        this.mapService = mapService;
        this.guidePointStore = guidePointStore;
        this.previewManager = previewManager;
    }

    public void handle(CommandContext<PlayerSource> context) {
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

        int portalCount = map.portals().size();
        int guideCount = guidePointStore.getGuidePoints(map.world()).size();
        boolean portalPreview = previewManager.hasPortalPreview(player.getUniqueId());
        boolean splinePreview = previewManager.hasSplinePreview(player.getUniqueId());

        player.sendMessage(Component.empty());
        player.sendMessage(Component.translatable("map.status.header"));
        player.sendMessage(Component.translatable("map.status.map", Component.text(map.name().asString())));
        player.sendMessage(Component.translatable("map.status.display", map.displayName()));
        player.sendMessage(Component.translatable("map.status.world", Component.text(map.world())));
        player.sendMessage(Component.translatable("map.status.portals", Component.text(portalCount)));
        player.sendMessage(Component.translatable("map.status.guide_points", Component.text(guideCount)));
        player.sendMessage(Component.translatable("map.status.portal_preview",
                Component.text(portalPreview ? "ON" : "OFF")));
        player.sendMessage(Component.translatable("map.status.spline_preview",
                Component.text(splinePreview ? "ON" : "OFF")));
        player.sendMessage(Component.empty());
    }

    public static void register(PaperCommandManager<Source> commandManager, MapService mapService,
                                GuidePointStore guidePointStore, ParticlePreviewManager previewManager) {
        var cmd = new MapStatusCommand(mapService, guidePointStore, previewManager);
        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("map")
                .literal("status")
                .senderType(PlayerSource.class)
                .handler(cmd::handle)
        );
    }
}
