package net.elytrarace.setup.command;

import net.elytrarace.common.builder.CupDTOBuilder;
import net.elytrarace.common.cup.CupService;
import net.elytrarace.setup.util.SetupGuard;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import static org.incendo.cloud.parser.standard.StringParser.greedyStringParser;
import static org.incendo.cloud.parser.standard.StringParser.stringParser;

/**
 * Handles {@code /elytrarace cup create <name> <displayName>}.
 * Replaces the 4-step cup conversation with a single command.
 */
public class CupCreateCommand {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9_-]{1,64}$");

    private final CupService cupService;

    public CupCreateCommand(CupService cupService) {
        this.cupService = cupService;
    }

    public void handle(CommandContext<PlayerSource> context) {
        var player = context.sender().source();

        if (SetupGuard.getSetupHolder(player).isEmpty()) {
            player.sendActionBar(Component.translatable("error.portal.quick.no_setup"));
            return;
        }

        String name = context.get("name");
        String displayNameRaw = context.get("displayName");

        // Validate name
        if (!NAME_PATTERN.matcher(name.toLowerCase(Locale.ROOT)).matches()) {
            player.sendMessage(Component.translatable("error.cup.name.invalid"));
            return;
        }

        // Check uniqueness
        var exists = cupService.getCups().stream()
                .anyMatch(cup -> cup.name().value().equalsIgnoreCase(name));
        if (exists) {
            player.sendMessage(Component.translatable("error.cup.name.exists"));
            return;
        }

        // Parse display name as MiniMessage
        var displayName = MiniMessage.miniMessage().deserialize(displayNameRaw);
        var key = Key.key("cup", name.toLowerCase(Locale.ROOT));

        // Create and save
        cupService.addCup(CupDTOBuilder.create()
                .name(key)
                .displayName(displayName)
                .build()
        ).thenCompose(success -> {
            if (success) {
                player.sendActionBar(Component.translatable("cup.create.success")
                        .arguments(displayName));
                return cupService.saveCups();
            }
            player.sendMessage(Component.translatable("setup.cup.failed")
                    .arguments(displayName));
            return CompletableFuture.completedFuture(null);
        });
    }

    public static void register(PaperCommandManager<Source> commandManager, CupService cupService) {
        var cmd = new CupCreateCommand(cupService);
        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("cup")
                .literal("create")
                .required("name", stringParser())
                .required("displayName", greedyStringParser())
                .senderType(PlayerSource.class)
                .handler(cmd::handle)
        );
    }
}
