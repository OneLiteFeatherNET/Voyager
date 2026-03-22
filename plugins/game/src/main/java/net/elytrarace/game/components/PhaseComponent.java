package net.elytrarace.game.components;

import net.elytrarace.api.phase.LinearPhaseSeries;
import net.elytrarace.api.phase.Phase;
import net.elytrarace.common.ecs.Component;
import net.elytrarace.game.phase.EndPhase;
import net.elytrarace.game.phase.GamePhase;
import net.elytrarace.game.phase.LobbyPhase;
import net.elytrarace.game.phase.PreparationPhase;
import net.elytrarace.game.service.GameService;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Component that stores the current phase information.
 */
public record PhaseComponent(LinearPhaseSeries<Phase> phaseSeries) implements Component {
    
    /**
     * Creates a new PhaseComponent with a default phase series.
     */
    public static PhaseComponent create(GameService gameService) {
        LinearPhaseSeries<Phase> phaseSeries = new LinearPhaseSeries<>();
        phaseSeries.add(new PreparationPhase(gameService));
        phaseSeries.add(new LobbyPhase(gameService));
        phaseSeries.add(new GamePhase(gameService));
        phaseSeries.add(new EndPhase(gameService.getPlugin()));
        phaseSeries.start();
        return new PhaseComponent(phaseSeries);
    }
    
    /**
     * Creates a new PhaseComponent with a default phase series.
     */
    public static PhaseComponent create(JavaPlugin plugin) {
        LinearPhaseSeries<Phase> phaseSeries = new LinearPhaseSeries<>();
        // Create a simplified phase series without GameService dependency
        phaseSeries.add(new EndPhase(plugin));
        phaseSeries.start();
        return new PhaseComponent(phaseSeries);
    }
    
    /**
     * Gets the current phase.
     */
    public Phase getCurrentPhase() {
        return phaseSeries.getCurrentPhase();
    }
    
    /**
     * Updates the phase series.
     */
    public PhaseComponent withPhaseSeries(LinearPhaseSeries<Phase> phaseSeries) {
        return new PhaseComponent(phaseSeries);
    }
}