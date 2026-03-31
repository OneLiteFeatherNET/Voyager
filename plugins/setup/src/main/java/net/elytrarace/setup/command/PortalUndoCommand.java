package net.elytrarace.setup.command;

import net.elytrarace.common.builder.MapDTOBuilder;
import net.elytrarace.common.map.MapService;
import net.elytrarace.common.map.model.PortalDTO;
import net.elytrarace.setup.undo.UndoManager;
import net.elytrarace.setup.undo.UndoOperation;
import net.elytrarace.setup.util.SetupGuard;
import net.kyori.adventure.text.Component;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;

import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /elytrarace portal undo}.
 */
public class PortalUndoCommand {

    private final MapService mapService;
    private final UndoManager undoManager;

    public PortalUndoCommand(MapService mapService, UndoManager undoManager) {
        this.mapService = mapService;
        this.undoManager = undoManager;
    }

    public void handle(org.incendo.cloud.context.CommandContext<PlayerSource> context) {
        var player = context.sender().source();

        if (SetupGuard.getSetupHolder(player).isEmpty()) {
            player.sendMessage(Component.translatable("error.portal.quick.no_setup"));
            return;
        }

        var operationOpt = undoManager.pop(player.getUniqueId());
        if (operationOpt.isEmpty()) {
            player.sendMessage(Component.translatable("error.portal.undo.empty"));
            return;
        }

        var operation = operationOpt.get();
        var world = player.getWorld();
        var mapOpt = SetupGuard.getMapForWorld(mapService, world);
        if (mapOpt.isEmpty()) {
            player.sendMessage(Component.translatable("error.portal.quick.no_map"));
            return;
        }
        var map = mapOpt.get();

        // Verify the operation belongs to the current map
        if (!map.uuid().equals(operation.mapUuid())) {
            player.sendMessage(Component.translatable("error.portal.undo.wrong_map"));
            return;
        }

        var portals = new TreeSet<>(map.portals());

        switch (operation) {
            case UndoOperation.PlaceOperation place -> {
                portals.removeIf(p -> p.index() == place.portal().index());
                player.sendActionBar(Component.translatable("success.portal.undo.removed")
                        .arguments(Component.text(place.portal().index()),
                                Component.text(undoManager.undoSize(player.getUniqueId()))));
            }
            case UndoOperation.DeleteOperation delete -> {
                portals.add(delete.portal());
                player.sendActionBar(Component.translatable("success.portal.undo.restored")
                        .arguments(Component.text(delete.portal().index()),
                                Component.text(undoManager.undoSize(player.getUniqueId()))));
            }
            case UndoOperation.EditOperation edit -> {
                portals.removeIf(p -> p.index() == edit.newPortal().index());
                portals.add(edit.oldPortal());
                player.sendActionBar(Component.translatable("success.portal.undo.restored")
                        .arguments(Component.text(edit.oldPortal().index()),
                                Component.text(undoManager.undoSize(player.getUniqueId()))));
            }
        }

        var newMap = MapDTOBuilder.create().from(map).portals(portals).build();
        mapService.updateMap(newMap).thenCompose(success -> {
            if (success) {
                return mapService.saveMaps();
            }
            return CompletableFuture.completedFuture(null);
        });
    }

    public static void register(PaperCommandManager<Source> commandManager, MapService mapService, UndoManager undoManager) {
        var cmd = new PortalUndoCommand(mapService, undoManager);
        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("portal")
                .literal("undo")
                .senderType(PlayerSource.class)
                .handler(cmd::handle)
        );
    }
}
