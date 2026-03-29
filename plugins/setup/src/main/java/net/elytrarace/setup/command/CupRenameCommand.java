package net.elytrarace.setup.command;

import net.elytrarace.common.cup.CupService;
import net.elytrarace.common.cup.model.FileCupDTO;
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
 * Handles {@code /elytrarace cup rename <oldName> <newName> <newDisplayName>}.
 */
public class CupRenameCommand {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9_-]{1,64}$");

    private final CupService cupService;

    public CupRenameCommand(CupService cupService) {
        this.cupService = cupService;
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

        // Find existing cup
        var cupOpt = cupService.getCups().stream()
                .filter(c -> c.name().value().equalsIgnoreCase(oldName))
                .findFirst();
        if (cupOpt.isEmpty()) {
            player.sendMessage(Component.translatable("error.map.cup_name.not_found")
                    .arguments(Component.text(oldName)));
            return;
        }
        var cup = cupOpt.get();

        // Validate new name
        if (!NAME_PATTERN.matcher(newName.toLowerCase(Locale.ROOT)).matches()) {
            player.sendMessage(Component.translatable("error.cup.name.invalid"));
            return;
        }

        // Check uniqueness (unless same name for display-name-only change)
        if (!oldName.equalsIgnoreCase(newName)) {
            var exists = cupService.getCups().stream()
                    .anyMatch(c -> c.name().value().equalsIgnoreCase(newName));
            if (exists) {
                player.sendMessage(Component.translatable("error.cup.name.exists"));
                return;
            }
        }

        var newDisplayName = MiniMessage.miniMessage().deserialize(newDisplayNameRaw);
        var newKey = Key.key("cup", newName.toLowerCase(Locale.ROOT));

        // Create renamed cup — keep map list intact
        var renamedCup = new FileCupDTO(newKey, newDisplayName, cup.maps());

        cupService.removeCup(cup).thenCompose(removed -> {
            if (!removed) {
                player.sendMessage(Component.translatable("error.cup.rename.failed")
                        .arguments(Component.text(oldName)));
                return null;
            }
            return cupService.addCup(renamedCup);
        }).thenCompose(added -> {
            if (added != null && added) {
                player.sendActionBar(Component.translatable("cup.rename.success")
                        .arguments(Component.text(oldName), newDisplayName));
                return cupService.saveCups();
            }
            return null;
        });
    }

    public static void register(PaperCommandManager<Source> commandManager, CupService cupService) {
        var cmd = new CupRenameCommand(cupService);
        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("cup")
                .literal("rename")
                .required("oldName", stringParser(), SetupSuggestions.cupNames(cupService))
                .required("newName", stringParser())
                .required("newDisplayName", greedyStringParser())
                .senderType(PlayerSource.class)
                .handler(cmd::handle)
        );
    }
}
