package net.elytrarace.setup.command;

import net.elytrarace.common.map.MapService;
import net.elytrarace.common.map.model.FilePortalDTO;
import net.elytrarace.setup.util.FaweHelper;
import net.elytrarace.setup.util.SetupGuard;
import net.elytrarace.setup.util.SetupSuggestions;
import net.kyori.adventure.text.Component;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;

import static org.incendo.cloud.parser.standard.IntegerParser.integerParser;

/**
 * Handles {@code /elytrarace portal edit <index>} — loads a portal's vertices back
 * into the player's FAWE PolyhedralRegionSelector for re-adjustment.
 */
public class PortalEditCommand {

    private final MapService mapService;
    private final EditingContextManager editingContextManager;

    public PortalEditCommand(MapService mapService, EditingContextManager editingContextManager) {
        this.mapService = mapService;
        this.editingContextManager = editingContextManager;
    }

    public void handle(CommandContext<PlayerSource> context) {
        var player = context.sender().source();
        int index = context.get("index");

        if (SetupGuard.getSetupHolder(player).isEmpty()) {
            player.sendActionBar(Component.translatable("error.portal.quick.no_setup"));
            return;
        }

        // Check not already editing
        if (editingContextManager.isEditing(player.getUniqueId())) {
            var existing = editingContextManager.getContext(player.getUniqueId()).orElseThrow();
            player.sendMessage(Component.translatable("error.portal.edit.already_editing")
                    .arguments(Component.text(existing.portalIndex())));
            return;
        }

        var world = player.getWorld();
        var mapOpt = SetupGuard.getMapForWorld(mapService, world);
        if (mapOpt.isEmpty()) {
            player.sendActionBar(Component.translatable("error.portal.quick.no_map"));
            return;
        }
        var map = mapOpt.get();

        // Find portal
        var portalOpt = map.portals().stream()
                .filter(p -> p.index() == index)
                .findFirst();
        if (portalOpt.isEmpty()) {
            player.sendMessage(Component.translatable("error.portal.delete.not_found")
                    .arguments(Component.text(index)));
            return;
        }
        var portal = (FilePortalDTO) portalOpt.get();

        // Load vertices into FAWE selector
        FaweHelper.setPolyhedralSelector(player, portal.locations());

        // Store editing context
        editingContextManager.startEditing(player.getUniqueId(),
                new EditingContext(map.uuid(), index, portal));

        player.sendMessage(Component.translatable("portal.edit.start")
                .arguments(Component.text(index)));
    }

    public static void register(PaperCommandManager<Source> commandManager, MapService mapService,
                                EditingContextManager editingContextManager) {
        var cmd = new PortalEditCommand(mapService, editingContextManager);
        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("portal")
                .literal("edit")
                .required("index", integerParser(1), SetupSuggestions.portalIndices(mapService))
                .senderType(PlayerSource.class)
                .handler(cmd::handle)
        );
    }
}
