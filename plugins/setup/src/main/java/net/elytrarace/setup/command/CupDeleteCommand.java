package net.elytrarace.setup.command;

import net.elytrarace.common.cup.CupService;
import net.elytrarace.common.map.MapService;
import net.elytrarace.common.map.model.FileMapDTO;
import net.elytrarace.setup.util.SetupGuard;
import net.elytrarace.setup.util.SetupSuggestions;
import net.kyori.adventure.text.Component;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.incendo.cloud.parser.standard.StringParser.stringParser;

/**
 * Handles {@code /elytrarace cup delete <name>} — removes a cup after confirmation.
 * Maps assigned to the cup are kept; only the cup and its map references are removed.
 * Requires a second invocation within 30 seconds to confirm.
 */
public class CupDeleteCommand {

    private final CupService cupService;
    private final Map<UUID, PendingDelete> pendingConfirmations = new ConcurrentHashMap<>();

    public CupDeleteCommand(CupService cupService) {
        this.cupService = cupService;
    }

    public void handle(CommandContext<PlayerSource> context) {
        var player = context.sender().source();

        if (SetupGuard.getSetupHolder(player).isEmpty()) {
            player.sendMessage(Component.translatable("error.portal.quick.no_setup"));
            return;
        }

        String name = context.get("name");

        var cupOpt = cupService.getCups().stream()
                .filter(c -> c.name().value().equalsIgnoreCase(name)
                        || c.name().asString().equalsIgnoreCase(name))
                .findFirst();

        if (cupOpt.isEmpty()) {
            player.sendMessage(Component.translatable("error.cup.not_found")
                    .arguments(Component.text(name)));
            return;
        }
        var cup = cupOpt.get();

        var pending = pendingConfirmations.get(player.getUniqueId());
        if (pending != null && pending.cupName().equalsIgnoreCase(name)
                && System.currentTimeMillis() - pending.timestamp() < 30_000) {
            // Confirmed — delete
            pendingConfirmations.remove(player.getUniqueId());
            cupService.removeCup(cup).thenCompose(removed -> {
                if (!removed) {
                    player.sendMessage(Component.translatable("error.cup.delete.failed")
                            .arguments(Component.text(name)));
                    return java.util.concurrent.CompletableFuture.completedFuture(null);
                }
                player.sendActionBar(Component.translatable("cup.delete.success")
                        .arguments(cup.displayName()));
                return cupService.saveCups();
            });
        } else {
            // First invocation — ask for confirmation
            pendingConfirmations.put(player.getUniqueId(), new PendingDelete(name, System.currentTimeMillis()));
            player.sendMessage(Component.translatable("cup.delete.confirm")
                    .arguments(cup.displayName(), Component.text(name)));
        }
    }

    private record PendingDelete(String cupName, long timestamp) {}

    public static void register(PaperCommandManager<Source> commandManager, CupService cupService) {
        var cmd = new CupDeleteCommand(cupService);
        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("cup")
                .literal("delete")
                .required("name", stringParser(), SetupSuggestions.cupNames(cupService))
                .senderType(PlayerSource.class)
                .handler(cmd::handle)
        );
    }
}
