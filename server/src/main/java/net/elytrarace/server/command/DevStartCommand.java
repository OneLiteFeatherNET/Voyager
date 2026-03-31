package net.elytrarace.server.command;

import net.elytrarace.server.game.GameOrchestrator;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Player;
import net.minestom.server.MinecraftServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Local-only debug command that skips the lobby countdown and loads the first map immediately.
 * <p>
 * <b>Not intended for production.</b> Register this command only in local/dev mode by passing
 * {@code -Dvoyager.dev=true} as a JVM argument.
 *
 * <pre>Usage: /dev-start</pre>
 */
public final class DevStartCommand extends Command {

    private static final Logger LOGGER = LoggerFactory.getLogger(DevStartCommand.class);

    private final GameOrchestrator orchestrator;

    public DevStartCommand(GameOrchestrator orchestrator) {
        super("dev-start");
        this.orchestrator = orchestrator;

        setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) {
                LOGGER.info("[dev-start] Triggered from console");
                triggerMapLoad(null);
                return;
            }
            player.sendMessage(Component.text("[dev] Skipping lobby — loading map now...", NamedTextColor.YELLOW));
            triggerMapLoad(player);
        });
    }

    private void triggerMapLoad(Player source) {
        try {
            orchestrator.loadNextMap().whenComplete((v, err) -> {
                if (err != null) {
                    LOGGER.error("[dev-start] Failed to load map", err);
                    if (source != null) {
                        source.sendMessage(Component.text("[dev] Map load failed: " + err.getMessage(), NamedTextColor.RED));
                    }
                } else {
                    LOGGER.info("[dev-start] Map loaded successfully");
                    MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(p ->
                        p.sendMessage(Component.text("[dev] Map loaded!", NamedTextColor.GREEN))
                    );
                }
            });
        } catch (Exception e) {
            LOGGER.error("[dev-start] Error triggering map load", e);
        }
    }
}
