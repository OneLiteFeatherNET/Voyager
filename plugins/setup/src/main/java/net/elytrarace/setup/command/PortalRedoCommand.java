package net.elytrarace.setup.command;

import net.elytrarace.common.builder.MapDTOBuilder;
import net.elytrarace.common.map.MapService;
import net.elytrarace.common.map.model.PortalDTO;
import net.elytrarace.setup.undo.UndoManager;
import net.elytrarace.setup.undo.UndoOperation;
import net.elytrarace.setup.util.SetupGuard;
import net.kyori.adventure.text.Component;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;

import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /elytrarace portal redo}.
 */
public class PortalRedoCommand {

    private final MapService mapService;
    private final UndoManager undoManager;

    public PortalRedoCommand(MapService mapService, UndoManager undoManager) {
        this.mapService = mapService;
        this.undoManager = undoManager;
    }

    public void handle(CommandContext<PlayerSource> context) {
        var player = context.sender().source();

        if (SetupGuard.getSetupHolder(player).isEmpty()) {
            player.sendMessage(Component.translatable("error.portal.quick.no_setup"));
            return;
        }

        var operationOpt = undoManager.redo(player.getUniqueId());
        if (operationOpt.isEmpty()) {
            player.sendMessage(Component.translatable("error.portal.redo.empty"));
            return;
        }

        var operation = operationOpt.get();
        var mapOpt = SetupGuard.getMapForWorld(mapService, player.getWorld());
        if (mapOpt.isEmpty()) {
            player.sendMessage(Component.translatable("error.portal.quick.no_map"));
            return;
        }
        var map = mapOpt.get();

        if (!map.uuid().equals(operation.mapUuid())) {
            player.sendMessage(Component.translatable("error.portal.undo.wrong_map"));
            return;
        }

        var portals = new TreeSet<>(map.portals());

        // Redo is the inverse of undo
        switch (operation) {
            case UndoOperation.PlaceOperation place -> {
                portals.add(place.portal());
                player.sendActionBar(Component.translatable("success.portal.redo.placed")
                        .arguments(Component.text(place.portal().index()),
                                Component.text(undoManager.redoSize(player.getUniqueId()))));
            }
            case UndoOperation.DeleteOperation delete -> {
                portals.removeIf(p -> p.index() == delete.portal().index());
                player.sendActionBar(Component.translatable("success.portal.redo.removed")
                        .arguments(Component.text(delete.portal().index()),
                                Component.text(undoManager.redoSize(player.getUniqueId()))));
            }
            case UndoOperation.EditOperation edit -> {
                portals.removeIf(p -> p.index() == edit.oldPortal().index());
                portals.add(edit.newPortal());
                player.sendActionBar(Component.translatable("success.portal.redo.placed")
                        .arguments(Component.text(edit.newPortal().index()),
                                Component.text(undoManager.redoSize(player.getUniqueId()))));
            }
        }

        var newMap = MapDTOBuilder.create().from(map).portals(portals).build();
        mapService.updateMap(newMap).thenCompose(success -> {
            if (success) return mapService.saveMaps();
            return CompletableFuture.completedFuture(null);
        });
    }

    public static void register(PaperCommandManager<Source> commandManager, MapService mapService,
                                UndoManager undoManager) {
        var cmd = new PortalRedoCommand(mapService, undoManager);
        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("portal")
                .literal("redo")
                .senderType(PlayerSource.class)
                .handler(cmd::handle)
        );
    }
}
