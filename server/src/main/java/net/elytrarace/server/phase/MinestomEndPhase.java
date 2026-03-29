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
 * End phase for the Minestom server.
 * <p>
 * Counts down from a configurable number of ticks (default 100 ticks = 5 seconds at 20 TPS)
 * while displaying the results and remaining time to all players. When the countdown
 * finishes, the server is stopped.
 */
public final class MinestomEndPhase extends TimedPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinestomEndPhase.class);
    private static final int DEFAULT_END_TICKS = 100;

    private final int endTicks;

    public MinestomEndPhase(PhaseScheduler scheduler, EventRegistrar eventRegistrar) {
        this(scheduler, eventRegistrar, DEFAULT_END_TICKS);
    }

    public MinestomEndPhase(PhaseScheduler scheduler, EventRegistrar eventRegistrar, int endTicks) {
        super("End", scheduler, eventRegistrar, 20, false);
        this.endTicks = endTicks;
        setEndTicks(0);
        setTickDirection(TickDirection.DOWN);
    }

    @Override
    public void onStart() {
        setCurrentTicks(endTicks);
        super.onStart();
        LOGGER.info("End phase started — showing results for {} seconds", endTicks);
        // TODO: Calculate and display final scoreboard / top three
    }

    @Override
    public void onUpdate() {
        var formattedTime = Strings.getTimeString(TimeFormat.MM_SS, getCurrentTicks());
        var message = Component.translatable("phase.end.time", Component.text(formattedTime));

        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            player.sendActionBar(message);
        }
    }

    @Override
    protected void onFinish() {
        LOGGER.info("End phase finished — stopping server");
        MinecraftServer.stopCleanly();
    }
}
