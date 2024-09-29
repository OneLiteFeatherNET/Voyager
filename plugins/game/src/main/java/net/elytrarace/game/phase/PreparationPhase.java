package net.elytrarace.game.phase;

import net.elytrarace.api.phase.TickDirection;
import net.elytrarace.api.phase.TimedPhase;
import net.elytrarace.game.ElytraRace;
import net.elytrarace.game.service.GameService;
import org.bukkit.Bukkit;

public class PreparationPhase extends TimedPhase {

    private final GameService game;

    public PreparationPhase(GameService game) {
        super("Preparation", game.getPlugin(), 20, true);
        this.game = game;
        setEndTicks(15);
        setCurrentTicks(0);
        setTickDirection(TickDirection.UP);
    }

    @Override
    public void onStart() {
        super.onStart();
        this.getPlugin().getLogger().info("Preparation phase has started!");
    }

    @Override
    protected void onFinish() {
        if (getPlugin() instanceof ElytraRace elytraRace) {
            if (game.getCurrentCup().isEmpty()) {
                elytraRace.getLogger().warning("No cup has been set, shutting down server...");
                Bukkit.shutdown();
            }
        }
    }

    @Override
    public void onUpdate() {

    }
}
