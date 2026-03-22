package net.elytrarace.game.system;

import net.elytrarace.common.ecs.Component;
import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.System;
import net.elytrarace.game.components.CurrentMapComponent;
import net.elytrarace.game.components.SimplePhaseComponent;
import net.elytrarace.game.components.SimplePhaseComponent.PhaseState;
import net.elytrarace.game.util.ElytraMarkers;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Set;

/**
 * System that manages game phases using the flattened component architecture.
 */
public class SimplePhaseSystem implements System {

    private static final ComponentLogger LOGGER = ComponentLogger.logger(SimplePhaseSystem.class);

    @Override
    public Set<Class<? extends Component>> getRequiredComponents() {
        return Set.of(SimplePhaseComponent.class);
    }

    @Override
    public void process(Entity entity, float deltaTime) {
        SimplePhaseComponent phaseComponent = entity.getComponent(SimplePhaseComponent.class);

        // Skip if the phase is finished or skipping
        if (phaseComponent.isFinished() || phaseComponent.isSkipping()) {
            return;
        }

        // Update the phase based on its name
        switch (phaseComponent.phaseName()) {
            case "Preparation":
                processPreparationPhase(entity, phaseComponent, deltaTime);
                break;
            case "Lobby":
                processLobbyPhase(entity, phaseComponent, deltaTime);
                break;
            case "Game":
                processGamePhase(entity, phaseComponent, deltaTime);
                break;
            case "End":
                processEndPhase(entity, phaseComponent, deltaTime);
                break;
            default:
                LOGGER.warn(ElytraMarkers.ECS, "Unknown phase: {}", phaseComponent.phaseName());
                break;
        }
    }

    /**
     * Processes the preparation phase.
     */
    private void processPreparationPhase(Entity entity, SimplePhaseComponent phaseComponent, float deltaTime) {
        // Increment the current ticks
        int currentTicks = phaseComponent.currentTicks() + 1;

        // Update the phase component
        SimplePhaseComponent updatedComponent = phaseComponent.withTicks(currentTicks);
        entity.removeComponent(SimplePhaseComponent.class);
        entity.addComponent(updatedComponent);

        // Check if the phase is complete
        if (currentTicks >= phaseComponent.maxTicks()) {
            // Transition to the lobby phase
            entity.removeComponent(SimplePhaseComponent.class);
            entity.addComponent(SimplePhaseComponent.createLobby());
        }
    }

    /**
     * Processes the lobby phase.
     */
    private void processLobbyPhase(Entity entity, SimplePhaseComponent phaseComponent, float deltaTime) {
        // Increment the current ticks
        int currentTicks = phaseComponent.currentTicks() + 1;

        // Update the phase component
        SimplePhaseComponent updatedComponent = phaseComponent.withTicks(currentTicks);
        entity.removeComponent(SimplePhaseComponent.class);
        entity.addComponent(updatedComponent);

        // Check if the phase is complete
        if (currentTicks >= phaseComponent.maxTicks()) {
            // Transition to the game phase
            entity.removeComponent(SimplePhaseComponent.class);
            entity.addComponent(SimplePhaseComponent.createGame());
        }
    }

    /**
     * Processes the game phase.
     */
    private void processGamePhase(Entity entity, SimplePhaseComponent phaseComponent, float deltaTime) {
        // Check if the entity has a CurrentMapComponent
        if (entity.hasComponent(CurrentMapComponent.class)) {
            CurrentMapComponent mapComponent = entity.getComponent(CurrentMapComponent.class);

            // If there's no current map, transition to the end phase
            if (!mapComponent.hasMap()) {
                entity.removeComponent(SimplePhaseComponent.class);
                entity.addComponent(SimplePhaseComponent.createEnd());
                return;
            }
        }

        // Increment the current ticks
        int currentTicks = phaseComponent.currentTicks() + 1;

        // Update the phase component
        SimplePhaseComponent updatedComponent = phaseComponent.withTicks(currentTicks);
        entity.removeComponent(SimplePhaseComponent.class);
        entity.addComponent(updatedComponent);
    }

    /**
     * Processes the end phase.
     */
    private void processEndPhase(Entity entity, SimplePhaseComponent phaseComponent, float deltaTime) {
        // Increment the current ticks
        int currentTicks = phaseComponent.currentTicks() + 1;

        // Update the phase component
        SimplePhaseComponent updatedComponent = phaseComponent.withTicks(currentTicks);
        entity.removeComponent(SimplePhaseComponent.class);
        entity.addComponent(updatedComponent);

        // Check if the phase is complete
        if (currentTicks >= phaseComponent.maxTicks()) {
            // Transition back to the preparation phase
            entity.removeComponent(SimplePhaseComponent.class);
            entity.addComponent(SimplePhaseComponent.createPreparation());
        }
    }

    /**
     * Forces the current phase to start in a specified number of ticks.
     * 
     * @param entity The entity with the SimplePhaseComponent
     * @param player The player who initiated the force start
     * @param ticks The number of ticks until the phase should start
     * @return true if the phase was forced to start, false otherwise
     */
    public boolean forceStart(Entity entity, Player player, int ticks) {
        if (!entity.hasComponent(SimplePhaseComponent.class)) {
            return false;
        }

        SimplePhaseComponent phaseComponent = entity.getComponent(SimplePhaseComponent.class);

        // Only allow forcing the lobby phase
        if (!"Lobby".equals(phaseComponent.phaseName())) {
            return false;
        }

        // Update the phase component with the new ticks
        SimplePhaseComponent updatedComponent = phaseComponent.withTicks(phaseComponent.maxTicks() - ticks);
        entity.removeComponent(SimplePhaseComponent.class);
        entity.addComponent(updatedComponent);

        // Notify the player
        player.sendMessage(net.kyori.adventure.text.Component.translatable("phase.lobby.force", 
                net.kyori.adventure.text.Component.translatable("plugin.prefix"), 
                net.kyori.adventure.text.Component.text(ticks)));

        return true;
    }
}
