package net.elytrarace.setup.command;

import net.elytrarace.common.builder.MapDTOBuilder;
import net.elytrarace.common.map.MapService;
import net.elytrarace.setup.util.SetupGuard;
import net.elytrarace.setup.util.SetupSuggestions;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;

import java.util.Locale;
import java.util.regex.Pattern;

import static org.incendo.cloud.parser.standard.StringParser.greedyStringParser;
import static org.incendo.cloud.parser.standard.StringParser.stringParser;

/**
 * Handles {@code /elytrarace map rename <oldName> <newName> <newDisplayName>}.
 * Renames a map's key and display name. UUID stays the same so cup references remain valid.
 */
public class MapRenameCommand {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9_]{1,64}$");

    private final MapService mapService;

    public MapRenameCommand(MapService mapService) {
        this.mapService = mapService;
    }

    public void handle(CommandContext<PlayerSource> context) {
        var player = context.sender().source();

        if (SetupGuard.getSetupHolder(player).isEmpty()) {
            player.sendActionBar(Component.translatable("error.portal.quick.no_setup"));
            return;
        }

        String oldName = context.get("oldName");
        String newName = context.get("newName");
        String newDisplayNameRaw = context.get("newDisplayName");

        // Find existing map
        var mapOpt = mapService.getMaps().stream()
                .filter(m -> m.name().value().equalsIgnoreCase(oldName)
                        || m.name().asString().equalsIgnoreCase(oldName))
                .findFirst();
        if (mapOpt.isEmpty()) {
            player.sendMessage(Component.translatable("error.map.not_found")
                    .arguments(Component.text(oldName)));
            return;
        }
        var map = mapOpt.get();

        // Validate new name
        if (!NAME_PATTERN.matcher(newName.toLowerCase(Locale.ROOT)).matches()) {
            player.sendMessage(Component.translatable("error.map.name.invalid"));
            return;
        }

        // Check new name not already taken (unless renaming to same name for display-name-only change)
        if (!oldName.equalsIgnoreCase(newName)) {
            var nameExists = mapService.getMaps().stream()
                    .anyMatch(m -> m.name().value().equalsIgnoreCase(newName));
            if (nameExists) {
                player.sendMessage(Component.translatable("error.map.name.exists"));
                return;
            }
        }

        // Build renamed map — keep UUID, world, portals, author
        var newDisplayName = MiniMessage.miniMessage().deserialize(newDisplayNameRaw);
        var newKey = Key.key("map", newName.toLowerCase(Locale.ROOT));
        var renamedMap = MapDTOBuilder.create()
                .from(map)
                .name(newKey)
                .displayName(newDisplayName)
                .build();

        // Update: remove old, add new (UUID unchanged, cup references stay valid)
        mapService.removeMap(map).thenCompose(removed -> {
            if (!removed) {
                player.sendMessage(Component.translatable("error.map.rename.failed")
                        .arguments(Component.text(oldName)));
                return null;
            }
            return mapService.addMap(renamedMap);
        }).thenCompose(added -> {
            if (added != null && added) {
                player.sendActionBar(Component.translatable("map.rename.success")
                        .arguments(Component.text(oldName), newDisplayName));
                return mapService.saveMaps();
            }
            return null;
        });
    }

    public static void register(PaperCommandManager<Source> commandManager, MapService mapService) {
        var cmd = new MapRenameCommand(mapService);
        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("map")
                .literal("rename")
                .required("oldName", stringParser(), SetupSuggestions.mapNames(mapService))
                .required("newName", stringParser())
                .required("newDisplayName", greedyStringParser())
                .senderType(PlayerSource.class)
                .handler(cmd::handle)
        );
    }
}
