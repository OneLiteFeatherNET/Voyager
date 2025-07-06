package net.elytrarace.game.system;

import net.elytrarace.api.phase.Phase;
import net.elytrarace.api.phase.TickedPhase;
import net.elytrarace.common.ecs.Component;
import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.System;
import net.elytrarace.game.components.GameStateComponent;
import net.elytrarace.game.components.PhaseComponent;
import net.elytrarace.game.phase.LobbyPhase;
import net.elytrarace.game.util.ElytraMarkers;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Set;

/**
 * System that manages game phases.
 */
public class PhaseSystem implements System {

    private static final ComponentLogger LOGGER = ComponentLogger.logger(PhaseSystem.class);

    @Override
    public Set<Class<? extends Component>> getRequiredComponents() {
        return Set.of(PhaseComponent.class);
    }

    @Override
    public void process(Entity entity, float deltaTime) {
        PhaseComponent phaseComponent = entity.getComponent(PhaseComponent.class);
        Phase currentPhase = phaseComponent.getCurrentPhase();
        
        // Update the current phase if it's a TickedPhase
        if (currentPhase instanceof TickedPhase tickedPhase) {
            tickedPhase.onUpdate();
        }
        
        // If the entity also has a GameStateComponent, update the phase with game state
        if (entity.hasComponent(GameStateComponent.class)) {
            GameStateComponent gameStateComponent = entity.getComponent(GameStateComponent.class);
            
            // Update the phase with game state information
            if (currentPhase instanceof LobbyPhase lobbyPhase) {
                // Example: Update lobby phase with current map information
                gameStateComponent.getCurrentMap().ifPresent(map -> {
                    // Update lobby phase with map information
                });
            }
        }
    }
    
    /**
     * Forces the current phase to start in a specified number of ticks.
     * 
     * @param entity The entity with the PhaseComponent
     * @param player The player who initiated the force start
     * @param ticks The number of ticks until the phase should start
     * @return true if the phase was forced to start, false otherwise
     */
    public boolean forceStart(Entity entity, Player player, int ticks) {
        if (!entity.hasComponent(PhaseComponent.class)) {
            return false;
        }
        
        PhaseComponent phaseComponent = entity.getComponent(PhaseComponent.class);
        Phase currentPhase = phaseComponent.getCurrentPhase();
        
        if (currentPhase instanceof LobbyPhase lobbyPhase) {
            lobbyPhase.setCurrentTicks(ticks);
            player.sendMessage(net.kyori.adventure.text.Component.translatable("phase.lobby.force", 
                    net.kyori.adventure.text.Component.translatable("plugin.prefix"), 
                    net.kyori.adventure.text.Component.text(ticks)));
            return true;
        }
        
        return false;
    }
}