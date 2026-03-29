package net.elytrarace.server.phase;

import net.elytrarace.api.phase.EventRegistrar;
import net.elytrarace.api.phase.PhaseScheduler;
import net.elytrarace.api.phase.TickDirection;
import net.elytrarace.api.phase.TimedPhase;
import net.elytrarace.common.utils.Strings;
import net.elytrarace.common.utils.TimeFormat;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lobby phase for the Minestom server.
 * <p>
 * Counts down from a configurable number of seconds (default 120 ticks = 6 seconds at 20 TPS)
 * and displays the remaining time as an actionbar message to all online players.
 * When the countdown finishes, a map-switch signal is emitted via the configured callback.
 */
public final class MinestomLobbyPhase extends TimedPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinestomLobbyPhase.class);
    private static final int DEFAULT_LOBBY_TICKS = 120;

    private final int lobbyTicks;

    public MinestomLobbyPhase(PhaseScheduler scheduler, EventRegistrar eventRegistrar) {
        this(scheduler, eventRegistrar, DEFAULT_LOBBY_TICKS);
    }

    public MinestomLobbyPhase(PhaseScheduler scheduler, EventRegistrar eventRegistrar, int lobbyTicks) {
        super("Lobby", scheduler, eventRegistrar, 20, false);
        this.lobbyTicks = lobbyTicks;
        setEndTicks(0);
        setTickDirection(TickDirection.DOWN);
    }

    @Override
    public void onStart() {
        setCurrentTicks(lobbyTicks);
        super.onStart();
        LOGGER.info("Lobby phase started — counting down from {} seconds", lobbyTicks);
    }

    @Override
    public void onUpdate() {
        var formattedTime = Strings.getTimeString(TimeFormat.MM_SS, getCurrentTicks());
        var message = Component.translatable("phase.lobby.time", Component.text(formattedTime));

        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            player.sendActionBar(message);
        }
    }

    @Override
    protected void onFinish() {
        LOGGER.info("Lobby phase finished — signaling map switch");
        // The LinearPhaseSeries finishedCallback will advance to the next phase.
        // Concrete map-switch logic (teleport, instance swap) will be wired externally.
    }
}
