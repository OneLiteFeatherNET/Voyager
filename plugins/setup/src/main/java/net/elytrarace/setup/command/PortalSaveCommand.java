package net.elytrarace.setup.command;

import com.fastasyncworldedit.core.regions.PolyhedralRegion;
import net.elytrarace.common.builder.MapDTOBuilder;
import net.elytrarace.common.builder.PortalDTOBuilder;
import net.elytrarace.common.map.MapService;
import net.elytrarace.common.map.model.FilePortalDTO;
import net.elytrarace.setup.preview.ParticlePreviewManager;
import net.elytrarace.setup.undo.UndoManager;
import net.elytrarace.setup.undo.UndoOperation;
import net.elytrarace.setup.util.FaweHelper;
import net.elytrarace.setup.util.SetupGuard;
import net.kyori.adventure.text.Component;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;

import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /elytrarace portal save} — saves the edited FAWE region back
 * to the portal at the stored index, replacing the old geometry.
 */
public class PortalSaveCommand {

    private final MapService mapService;
    private final EditingContextManager editingContextManager;
    private final UndoManager undoManager;
    private final ParticlePreviewManager previewManager;

    public PortalSaveCommand(MapService mapService, EditingContextManager editingContextManager,
                             UndoManager undoManager, ParticlePreviewManager previewManager) {
        this.mapService = mapService;
        this.editingContextManager = editingContextManager;
        this.undoManager = undoManager;
        this.previewManager = previewManager;
    }

    public void handle(CommandContext<PlayerSource> context) {
        var player = context.sender().source();

        if (SetupGuard.getSetupHolder(player).isEmpty()) {
            player.sendMessage(Component.translatable("error.portal.quick.no_setup"));
            return;
        }

        // Must be in editing mode
        var ctxOpt = editingContextManager.getContext(player.getUniqueId());
        if (ctxOpt.isEmpty()) {
            player.sendMessage(Component.translatable("error.portal.edit.not_editing"));
            return;
        }
        var editCtx = ctxOpt.get();

        // Get current FAWE region
        var selectorOpt = FaweHelper.getPolyhedralSelector(player);
        if (selectorOpt.isEmpty()) {
            player.sendMessage(Component.translatable("error.portal.quick.wrong_region_type"));
            return;
        }
        var selector = selectorOpt.get();
        if (selector.getVertices().size() <= 3) {
            player.sendMessage(Component.translatable("error.portal.quick.too_few_vertices")
                    .arguments(Component.text(selector.getVertices().size())));
            return;
        }

        // Extract updated region
        var region = (PolyhedralRegion) selector.getRegion();
        var locations = FaweHelper.extractLocations(region);

        // Build updated portal with same index
        FilePortalDTO updatedPortal = PortalDTOBuilder.create()
                .index(editCtx.portalIndex())
                .locations(locations)
                .build();

        // Find current map and replace portal
        var world = player.getWorld();
        var mapOpt = SetupGuard.getMapForWorld(mapService, world);
        if (mapOpt.isEmpty()) {
            player.sendMessage(Component.translatable("error.portal.quick.no_map"));
            return;
        }
        var map = mapOpt.get();

        var portals = new TreeSet<>(map.portals());
        portals.removeIf(p -> p.index() == editCtx.portalIndex());
        portals.add(updatedPortal);
        var newMap = MapDTOBuilder.create().from(map).portals(portals).build();

        // Push undo: removes new portal + re-adds original
        undoManager.push(player.getUniqueId(),
                new UndoOperation.EditOperation(map.uuid(), editCtx.originalPortal(), updatedPortal));

        // Save and clear editing state
        mapService.updateMap(newMap).thenCompose(success -> {
            if (success) {
                player.sendActionBar(Component.translatable("portal.edit.saved")
                        .arguments(Component.text(editCtx.portalIndex())));
                previewManager.refreshLabels(world.getName());
                return mapService.saveMaps();
            }
            player.sendMessage(Component.translatable("error.portal.quick.save_failed")
                    .arguments(map.displayName(), Component.text(editCtx.portalIndex())));
            return CompletableFuture.completedFuture(null);
        });

        editingContextManager.clearContext(player.getUniqueId());
        selector.clear();
    }

    public static void register(PaperCommandManager<Source> commandManager, MapService mapService,
                                EditingContextManager editingContextManager, UndoManager undoManager,
                                ParticlePreviewManager previewManager) {
        var cmd = new PortalSaveCommand(mapService, editingContextManager, undoManager, previewManager);
        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("portal")
                .literal("save")
                .senderType(PlayerSource.class)
                .handler(cmd::handle)
        );
    }
}
