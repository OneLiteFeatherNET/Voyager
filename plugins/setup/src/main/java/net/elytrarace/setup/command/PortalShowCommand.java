package net.elytrarace.setup.command;

import net.elytrarace.setup.preview.ParticlePreviewManager;
import net.elytrarace.setup.session.SetupSessionManager;
import net.elytrarace.setup.util.SetupGuard;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;

/**
 * Handles {@code /elytrarace portal show} — toggles particle preview of all portals.
 */
public class PortalShowCommand {

    private final ParticlePreviewManager previewManager;
    private final Plugin plugin;
    private final SetupSessionManager sessionManager;

    public PortalShowCommand(ParticlePreviewManager previewManager, Plugin plugin, SetupSessionManager sessionManager) {
        this.previewManager = previewManager;
        this.plugin = plugin;
        this.sessionManager = sessionManager;
    }

    public void handle(CommandContext<PlayerSource> context) {
        var player = context.sender().source();

        if (SetupGuard.getSetupHolder(player).isEmpty()) {
            player.sendMessage(Component.translatable("error.portal.quick.no_setup"));
            return;
        }

        // world.spawn() inside togglePortals requires the main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            boolean enabled = previewManager.togglePortals(player.getUniqueId());
            sessionManager.get(player.getUniqueId()).ifPresent(s -> s.setPortalPreview(enabled));
            if (enabled) {
                player.sendActionBar(Component.translatable("portal.show.enabled"));
            } else {
                player.sendActionBar(Component.translatable("portal.show.disabled"));
            }
        });
    }

    public static void register(PaperCommandManager<Source> commandManager, ParticlePreviewManager previewManager, Plugin plugin, SetupSessionManager sessionManager) {
        var cmd = new PortalShowCommand(previewManager, plugin, sessionManager);
        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("portal")
                .literal("show")
                .senderType(PlayerSource.class)
                .handler(cmd::handle)
        );
    }
}
