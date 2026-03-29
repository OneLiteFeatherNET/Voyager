package net.elytrarace.server.phase;

import net.elytrarace.common.utils.Strings;
import net.elytrarace.common.utils.TimeFormat;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;

/**
 * Shared UI utilities for phase implementations.
 * <p>
 * Provides common functionality used by multiple phases (e.g. lobby and end phases)
 * to avoid code duplication.
 */
public final class PhaseUiHelper {

    private PhaseUiHelper() {
        // utility class
    }

    /**
     * Broadcasts a translatable actionbar message with the formatted remaining time
     * to all online players.
     *
     * @param translationKey the translation key for the message (e.g. "phase.lobby.time")
     * @param currentTicks   the current tick count to format as MM:SS
     */
    public static void broadcastTimeActionBar(String translationKey, int currentTicks) {
        var formattedTime = Strings.getTimeString(TimeFormat.MM_SS, currentTicks);
        var message = Component.translatable(translationKey, Component.text(formattedTime));

        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            player.sendActionBar(message);
        }
    }
}
