package net.elytrarace.server.phase;

import net.minestom.server.utils.time.TimeUnit;
import net.theevilreaper.xerus.api.phase.TickDirection;
import net.theevilreaper.xerus.api.phase.TimedPhase;
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
    private final Runnable onMapSwitch;
    private boolean finishing = false;

    public MinestomLobbyPhase() {
        this(DEFAULT_LOBBY_TICKS, null);
    }

    public MinestomLobbyPhase(int lobbyTicks) {
        this(lobbyTicks, null);
    }

    public MinestomLobbyPhase(int lobbyTicks, Runnable onMapSwitch) {
        super("lobby", TimeUnit.SERVER_TICK, 20);
        this.lobbyTicks = lobbyTicks;
        this.onMapSwitch = onMapSwitch;
        setEndTicks(0);
        setCurrentTicks(lobbyTicks);
        setTickDirection(TickDirection.DOWN);
    }

    @Override
    public void onStart() {
        finishing = false;
        setCurrentTicks(lobbyTicks);
        super.onStart();
        LOGGER.info("Lobby phase started — counting down from {} seconds", lobbyTicks);
    }

    @Override
    public void finish() {
        if (finishing) {
            return;
        }
        finishing = true;
        super.finish();
    }

    @Override
    public void onUpdate() {
        PhaseUiHelper.broadcastTimeActionBar("phase.lobby.time", getCurrentTicks());
    }

    @Override
    protected void onFinish() {
        LOGGER.info("Lobby phase finished — signaling map switch");
        if (onMapSwitch != null) {
            onMapSwitch.run();
        }
    }
}
