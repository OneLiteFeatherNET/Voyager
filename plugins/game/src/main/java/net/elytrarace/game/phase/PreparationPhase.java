package net.elytrarace.game.phase;

import net.elytrarace.api.phase.TickDirection;
import net.elytrarace.api.phase.TimedPhase;
import net.elytrarace.game.service.GameService;
import org.bukkit.Bukkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PreparationPhase extends TimedPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(PreparationPhase.class);
    private final GameService game;

    public PreparationPhase(GameService game) {
        super("Preparation", game.getPhaseScheduler(), game.getEventRegistrar(), 20, true);
        this.game = game;
        setEndTicks(15);
        setCurrentTicks(0);
        setTickDirection(TickDirection.UP);
    }

    @Override
    public void onStart() {
        super.onStart();
        LOGGER.info("Preparation phase has started!");
    }

    @Override
    protected void onFinish() {
        if (game.getCurrentCup().isEmpty()) {
            LOGGER.warn("No cup has been set, shutting down server...");
            Bukkit.shutdown();
        }
    }

    @Override
    public void onUpdate() {

    }
}
