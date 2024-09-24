package net.elytrarace.game.phase;

import net.elytrarace.api.phase.TickingPhase;
import org.bukkit.plugin.java.JavaPlugin;

public class GamePhase extends TickingPhase {
    public GamePhase(JavaPlugin game) {
        super("Game", game, 1, true);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onUpdate() {

    }

    @Override
    public void finish() {
        super.finish();
    }
}
