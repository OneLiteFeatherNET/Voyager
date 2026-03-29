package net.elytrarace.setup.command;

import net.elytrarace.setup.preview.ParticlePreviewManager;
import net.elytrarace.setup.util.SetupGuard;
import net.kyori.adventure.text.Component;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;

/**
 * Handles {@code /elytrarace portal path} — toggles the spline ideal line preview.
 */
public class PortalPathCommand {

    private final ParticlePreviewManager previewManager;

    public PortalPathCommand(ParticlePreviewManager previewManager) {
        this.previewManager = previewManager;
    }

    public void handle(CommandContext<PlayerSource> context) {
        var player = context.sender().source();

        if (SetupGuard.getSetupHolder(player).isEmpty()) {
            player.sendActionBar(Component.translatable("error.portal.quick.no_setup"));
            return;
        }

        boolean enabled = previewManager.toggleSpline(player.getUniqueId());
        if (enabled) {
            player.sendActionBar(Component.translatable("portal.path.enabled"));
        } else {
            player.sendActionBar(Component.translatable("portal.path.disabled"));
        }
    }

    public static void register(PaperCommandManager<Source> commandManager, ParticlePreviewManager previewManager) {
        var cmd = new PortalPathCommand(previewManager);
        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("portal")
                .literal("path")
                .senderType(PlayerSource.class)
                .handler(cmd::handle)
        );
    }
}
