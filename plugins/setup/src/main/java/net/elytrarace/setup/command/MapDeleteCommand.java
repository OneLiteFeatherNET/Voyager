package net.elytrarace.setup.command;

import net.elytrarace.common.cup.CupService;
import net.elytrarace.common.cup.model.FileCupDTO;
import net.elytrarace.common.map.MapService;
import net.elytrarace.setup.util.SetupGuard;
import net.elytrarace.setup.util.SetupSuggestions;
import net.kyori.adventure.text.Component;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;

import java.util.ArrayList;

import static org.incendo.cloud.parser.standard.StringParser.stringParser;

/**
 * Handles {@code /elytrarace map delete <name>}.
 * Removes a map and cleans up cup references.
 */
public class MapDeleteCommand {

    private final MapService mapService;
    private final CupService cupService;

    public MapDeleteCommand(MapService mapService, CupService cupService) {
        this.mapService = mapService;
        this.cupService = cupService;
    }

    public void handle(CommandContext<PlayerSource> context) {
        var player = context.sender().source();

        if (SetupGuard.getSetupHolder(player).isEmpty()) {
            player.sendActionBar(Component.translatable("error.portal.quick.no_setup"));
            return;
        }

        String name = context.get("name");

        // Find map
        var mapOpt = mapService.getMaps().stream()
                .filter(m -> m.name().value().equalsIgnoreCase(name)
                        || m.name().asString().equalsIgnoreCase(name))
                .findFirst();
        if (mapOpt.isEmpty()) {
            player.sendMessage(Component.translatable("error.map.not_found")
                    .arguments(Component.text(name)));
            return;
        }
        var map = mapOpt.get();

        // Remove from map service (also cleans up per-world directory)
        mapService.removeMap(map).thenCompose(removed -> {
            if (!removed) {
                player.sendMessage(Component.translatable("error.map.delete.failed")
                        .arguments(Component.text(name)));
                return null;
            }
            return mapService.saveMaps();
        }).thenRun(() -> {
            // Remove map UUID from all cups that reference it
            for (var cup : cupService.getCups()) {
                if (cup.maps().contains(map.uuid())) {
                    var updatedMaps = new ArrayList<>(cup.maps());
                    updatedMaps.remove(map.uuid());
                    var updatedCup = new FileCupDTO(cup.name(), cup.displayName(), updatedMaps);
                    cupService.updateCup(updatedCup).thenCompose(s -> cupService.saveCups());
                }
            }
            player.sendActionBar(Component.translatable("map.delete.success")
                    .arguments(map.displayName()));
        });
    }

    public static void register(PaperCommandManager<Source> commandManager, MapService mapService,
                                CupService cupService) {
        var cmd = new MapDeleteCommand(mapService, cupService);
        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("map")
                .literal("delete")
                .required("name", stringParser(), SetupSuggestions.mapNames(mapService))
                .senderType(PlayerSource.class)
                .handler(cmd::handle)
        );
    }
}
