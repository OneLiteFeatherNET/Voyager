package net.elytrarace.setup.command;

import net.elytrarace.common.builder.MapDTOBuilder;
import net.elytrarace.common.map.MapService;
import net.elytrarace.common.map.model.FilePortalDTO;
import net.elytrarace.common.map.model.PortalDTO;
import net.elytrarace.setup.undo.UndoManager;
import net.elytrarace.setup.undo.UndoOperation;
import net.elytrarace.setup.util.SetupGuard;
import net.elytrarace.setup.util.SetupSuggestions;
import net.kyori.adventure.text.Component;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;

import java.util.TreeSet;

import static org.incendo.cloud.parser.standard.IntegerParser.integerParser;

/**
 * Handles {@code /elytrarace portal delete <index>}.
 */
public class PortalDeleteCommand {

    private final MapService mapService;
    private final UndoManager undoManager;

    public PortalDeleteCommand(MapService mapService, UndoManager undoManager) {
        this.mapService = mapService;
        this.undoManager = undoManager;
    }

    public void handle(org.incendo.cloud.context.CommandContext<PlayerSource> context) {
        var player = context.sender().source();
        int index = context.get("index");

        if (SetupGuard.getSetupHolder(player).isEmpty()) {
            player.sendActionBar(Component.translatable("error.portal.quick.no_setup"));
            return;
        }

        var world = player.getWorld();
        var mapOpt = SetupGuard.getMapForWorld(mapService, world);
        if (mapOpt.isEmpty()) {
            player.sendActionBar(Component.translatable("error.portal.quick.no_map"));
            return;
        }
        var map = mapOpt.get();

        // Find portal to delete
        var portalOpt = map.portals().stream()
                .filter(p -> p.index() == index)
                .findFirst();
        if (portalOpt.isEmpty()) {
            player.sendMessage(Component.translatable("error.portal.delete.not_found")
                    .arguments(Component.text(index)));
            return;
        }

        var portalToDelete = (FilePortalDTO) portalOpt.get();

        // Push to undo stack before deleting
        undoManager.push(player.getUniqueId(),
                new UndoOperation.DeleteOperation(map.uuid(), portalToDelete));

        // Remove portal and save
        var portals = new TreeSet<>(map.portals());
        portals.removeIf(p -> p.index() == index);
        var newMap = MapDTOBuilder.create().from(map).portals(portals).build();

        mapService.updateMap(newMap).thenCompose(success -> {
            if (success) {
                player.sendActionBar(Component.translatable("success.portal.delete")
                        .arguments(Component.text(index), Component.text(portals.size())));
                return mapService.saveMaps();
            }
            player.sendMessage(Component.translatable("error.portal.quick.save_failed")
                    .arguments(map.displayName(), Component.text(index)));
            return null;
        });
    }

    public static void register(PaperCommandManager<Source> commandManager, MapService mapService, UndoManager undoManager) {
        var cmd = new PortalDeleteCommand(mapService, undoManager);
        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("portal")
                .literal("delete")
                .required("index", integerParser(1), SetupSuggestions.portalIndices(mapService))
                .senderType(PlayerSource.class)
                .handler(cmd::handle)
        );
    }
}
