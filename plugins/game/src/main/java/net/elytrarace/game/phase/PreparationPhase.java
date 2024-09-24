package net.elytrarace.game.phase;

import net.elytrarace.api.phase.TickDirection;
import net.elytrarace.api.phase.TimedPhase;
import net.elytrarace.game.ElytraRace;
import org.bukkit.Bukkit;

public class PreparationPhase extends TimedPhase {
    public PreparationPhase(ElytraRace game) {
        super("Preparation", game, 20, true);
        setEndTicks(30);
        setCurrentTicks(0);
        setTickDirection(TickDirection.UP);
    }

    @Override
    public void onStart() {
        super.onStart();
        this.getPlugin().getLogger().info("Preparation phase has started!");
        if (getPlugin() instanceof ElytraRace elytraRace) elytraRace.init();
    }

    @Override
    protected void onFinish() {
        if (getPlugin() instanceof ElytraRace elytraRace) {
            if (elytraRace.getCurrentCup().isEmpty()) {
                elytraRace.getLogger().warning("No cup has been set, shutting down server...");
                Bukkit.shutdown();
            }
        }
    }

    @Override
    public void onUpdate() {

    }
}
