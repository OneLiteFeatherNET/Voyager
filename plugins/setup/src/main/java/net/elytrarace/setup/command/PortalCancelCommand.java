package net.elytrarace.setup.command;

import net.elytrarace.setup.util.SetupGuard;
import net.kyori.adventure.text.Component;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;

/**
 * Handles {@code /elytrarace portal cancel} — aborts an active portal edit
 * without saving and without ending the entire setup session.
 */
public class PortalCancelCommand {

    private final EditingContextManager editingContextManager;

    public PortalCancelCommand(EditingContextManager editingContextManager) {
        this.editingContextManager = editingContextManager;
    }

    public void handle(CommandContext<PlayerSource> context) {
        var player = context.sender().source();

        if (SetupGuard.getSetupHolder(player).isEmpty()) {
            player.sendActionBar(Component.translatable("error.portal.quick.no_setup"));
            return;
        }

        if (!editingContextManager.isEditing(player.getUniqueId())) {
            player.sendMessage(Component.translatable("portal.cancel.not_editing"));
            return;
        }

        var editCtx = editingContextManager.getContext(player.getUniqueId()).orElseThrow();
        editingContextManager.clearContext(player.getUniqueId());
        player.sendMessage(Component.translatable("portal.cancel.success")
                .arguments(Component.text(editCtx.portalIndex())));
    }

    public static void register(PaperCommandManager<Source> commandManager,
                                EditingContextManager editingContextManager) {
        var cmd = new PortalCancelCommand(editingContextManager);
        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("portal")
                .literal("cancel")
                .senderType(PlayerSource.class)
                .handler(cmd::handle)
        );
    }
}
