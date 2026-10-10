package net.elytrarace.game.system;

import net.elytrarace.common.ecs.Component;
import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.System;
import net.elytrarace.game.components.CurrentCupComponent;
import net.elytrarace.game.components.CurrentMapComponent;
import net.elytrarace.game.components.SessionComponent;
import net.elytrarace.game.components.SimpleWorldComponent;
import net.elytrarace.game.util.ElytraMarkers;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * System that manages the game state using the flattened component architecture.
 */
public class SimpleGameStateSystem implements System {

    private static final ComponentLogger LOGGER = ComponentLogger.logger(SimpleGameStateSystem.class);

    @Override
    public Set<Class<? extends Component>> getRequiredComponents() {
        return Set.of(SessionComponent.class);
    }

    @Override
    public void process(Entity entity, float deltaTime) {
        // This system doesn't need to do anything on regular updates
    }

    /**
     * Sets the current cup for the entity.
     */
    public void setCurrentCup(Entity entity, Key cupId, net.kyori.adventure.text.Component displayName, List<Key> mapIds) {
        // Ensure the entity has a session component
        if (!entity.hasComponent(SessionComponent.class)) {
            entity.addComponent(SessionComponent.create());
        }

        // Update or add the current cup component
        CurrentCupComponent cupComponent = CurrentCupComponent.create(cupId, displayName, mapIds);
        if (entity.hasComponent(CurrentCupComponent.class)) {
            entity.removeComponent(CurrentCupComponent.class);
        }
        entity.addComponent(cupComponent);

        LOGGER.info(ElytraMarkers.CUP, "The current cup has been set to: {}", cupId);
    }

    /**
     * Sets the current map for the entity.
     */
    public void setCurrentMap(Entity entity, Key mapId, String worldName, int mapIndex) {
        // Ensure the entity has a session component
        if (!entity.hasComponent(SessionComponent.class)) {
            entity.addComponent(SessionComponent.create());
        }

        // Update or add the current map component
        CurrentMapComponent mapComponent = CurrentMapComponent.create(mapId, worldName, mapIndex);
        if (entity.hasComponent(CurrentMapComponent.class)) {
            entity.removeComponent(CurrentMapComponent.class);
        }
        entity.addComponent(mapComponent);

        // Add or update the world component
        SimpleWorldComponent worldComponent = SimpleWorldComponent.create(worldName);
        if (entity.hasComponent(SimpleWorldComponent.class)) {
            entity.removeComponent(SimpleWorldComponent.class);
        }
        entity.addComponent(worldComponent);
    }

    /**
     * Switches to the next map in the current cup.
     */
    public void switchToNextMap(Entity entity) {
        // Ensure the entity has the required components
        if (!entity.hasComponent(SessionComponent.class) || 
            !entity.hasComponent(CurrentCupComponent.class) || 
            !entity.hasComponent(CurrentMapComponent.class)) {
            LOGGER.error(ElytraMarkers.CUP, "Cannot switch map: missing required components");
            return;
        }

        CurrentCupComponent cupComponent = entity.getComponent(CurrentCupComponent.class);
        CurrentMapComponent mapComponent = entity.getComponent(CurrentMapComponent.class);

        // If there's no current map, set the first map
        if (!mapComponent.hasMap()) {
            Key firstMapId = cupComponent.mapIds().isEmpty() ? null : cupComponent.mapIds().get(0);
            if (firstMapId == null) {
                LOGGER.error(ElytraMarkers.CUP, "Cannot switch map: cup has no maps");
                return;
            }

            // Set the first map
            setCurrentMap(entity, firstMapId, "world_" + firstMapId.value(), 0);
            return;
        }

        // Get the current map index
        int currentIndex = mapComponent.mapIndex();
        int nextIndex = currentIndex + 1;

        // Check if we've reached the end of the cup
        if (nextIndex >= cupComponent.mapIds().size()) {
            // Clear the current map
            entity.removeComponent(CurrentMapComponent.class);
            entity.addComponent(CurrentMapComponent.createEmpty());
            return;
        }

        // Set the next map
        Key nextMapId = cupComponent.mapIds().get(nextIndex);
        setCurrentMap(entity, nextMapId, "world_" + nextMapId.value(), nextIndex);
    }
}
