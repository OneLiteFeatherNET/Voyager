package net.elytrarace.setup.command;

import net.elytrarace.common.builder.MapDTOBuilder;
import net.elytrarace.common.cup.CupService;
import net.elytrarace.common.cup.model.FileCupDTO;
import net.elytrarace.common.map.MapService;
import net.elytrarace.setup.ElytraRace;
import net.elytrarace.setup.util.SetupGuard;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.persistence.PersistentDataType;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;
import net.elytrarace.setup.util.SetupSuggestions;
import java.util.concurrent.CompletableFuture;

import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Pattern;

import static org.incendo.cloud.parser.standard.StringParser.greedyStringParser;
import static org.incendo.cloud.parser.standard.StringParser.stringParser;

/**
 * Handles {@code /elytrarace map create <cup> <name> <displayName>}.
 * Auto-detects world from player position, auto-sets author from player name.
 * Replaces the 7-step map conversation with a single command.
 */
public class MapCreateCommand {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9_]{1,64}$");

    private final MapService mapService;
    private final CupService cupService;

    public MapCreateCommand(MapService mapService, CupService cupService) {
        this.mapService = mapService;
        this.cupService = cupService;
    }

    public void handle(CommandContext<PlayerSource> context) {
        var player = context.sender().source();

        if (SetupGuard.getSetupHolder(player).isEmpty()) {
            player.sendMessage(Component.translatable("error.portal.quick.no_setup"));
            return;
        }

        String cupName = context.get("cup");
        String name = context.get("name");
        String displayNameRaw = context.get("displayName");

        // Find cup
        var cupOpt = cupService.getCups().stream()
                .filter(c -> c.name().value().equalsIgnoreCase(cupName))
                .findFirst();
        if (cupOpt.isEmpty()) {
            player.sendMessage(Component.translatable("error.map.cup_name.not_found")
                    .arguments(Component.text(cupName)));
            return;
        }
        var cup = cupOpt.get();

        // Validate map name
        if (!NAME_PATTERN.matcher(name.toLowerCase(Locale.ROOT)).matches()) {
            player.sendMessage(Component.translatable("error.map.name.invalid"));
            return;
        }

        // Check name uniqueness
        var nameExists = mapService.getMaps().stream()
                .anyMatch(m -> m.name().value().equalsIgnoreCase(name));
        if (nameExists) {
            player.sendMessage(Component.translatable("error.map.name.exists"));
            return;
        }

        // Auto-detect world
        var world = player.getWorld();

        // Check world not already mapped
        var worldMapped = mapService.getMaps().stream()
                .anyMatch(m -> m.world().equalsIgnoreCase(world.getName()));
        if (worldMapped) {
            player.sendMessage(Component.translatable("error.map.world.already_mapped")
                    .arguments(Component.text(world.getName())));
            return;
        }

        // Parse display name and auto-set author
        var displayName = MiniMessage.miniMessage().deserialize(displayNameRaw);
        var author = player.displayName();
        var key = Key.key("map", name.toLowerCase(Locale.ROOT));

        // Create map
        var mapDTO = MapDTOBuilder.create()
                .name(key)
                .displayName(displayName)
                .author(author)
                .world(world.getName())
                .generateUUID()
                .build();

        // Update cup with new map UUID
        var updatedCup = new FileCupDTO(cup.name(), cup.displayName(), new ArrayList<>(cup.maps()));
        updatedCup.maps().add(mapDTO.uuid());

        // Save map, then update cup
        mapService.addMap(mapDTO)
                .thenCompose(success -> {
                    if (success) {
                        player.sendActionBar(Component.translatable("setup.map.added")
                                .arguments(displayName));
                        return mapService.saveMaps();
                    }
                    player.sendMessage(Component.translatable("setup.map.failed")
                            .arguments(displayName));
                    return CompletableFuture.completedFuture(null);
                })
                .thenCompose(v -> cupService.updateCup(updatedCup))
                .thenCompose(success -> {
                    if (success) {
                        return cupService.saveCups();
                    }
                    return CompletableFuture.completedFuture(null);
                })
                .thenAccept(v -> {
                    // Mark world as setup
                    world.getPersistentDataContainer().set(ElytraRace.WORLD_SETUP, PersistentDataType.BOOLEAN, true);
                    player.sendActionBar(Component.translatable("map.create.success")
                            .arguments(displayName, Component.text(world.getName()), cup.displayName()));
                });
    }

    public static void register(PaperCommandManager<Source> commandManager, MapService mapService,
                                CupService cupService) {
        var cmd = new MapCreateCommand(mapService, cupService);
        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("map")
                .literal("create")
                .required("cup", stringParser(), SetupSuggestions.cupNames(cupService))
                .required("name", stringParser())
                .required("displayName", greedyStringParser())
                .senderType(PlayerSource.class)
                .handler(cmd::handle)
        );
    }
}
